package xyz.bluspring.velocityrp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;

@Plugin(
        id = "velocityrp",
        name = "VelocityResourcePack",
        version = BuildConstants.VERSION,
        description = "Adds the ability to set the resource pack on Velocity.",
        authors = {"BluSpring"}
)
public class VelocityResourcePack {
    public static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Logger logger;
    private final ProxyServer proxy;

    private final Path dataDir;

    private ResourcePackInfo info;

    @Inject
    public VelocityResourcePack(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.logger = logger;
        this.proxy = proxy;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        load();

        this.proxy.getCommandManager().register(new BrigadierCommand(
                LiteralArgumentBuilder.<CommandSource>literal("rpv")
                        .requires(source -> source.hasPermission("velocityrp.admin"))
                        .then(
                                RequiredArgumentBuilder.<CommandSource, String>argument("url", StringArgumentType.string())
                                        .then(
                                                RequiredArgumentBuilder.<CommandSource, String>argument("hash", StringArgumentType.string())
                                                        .executes((ctx) -> {
                                                            var url = StringArgumentType.getString(ctx, "url");
                                                            var hash = StringArgumentType.getString(ctx, "hash");

                                                            this.info = this.proxy.createResourcePackBuilder(url)
                                                                    .setHash(HexFormat.of().parseHex(hash))
                                                                    .setPrompt(this.info == null ? Component.text("This server requires you to install this resource pack!") : this.info.getPrompt())
                                                                    .setShouldForce(true)
                                                                    .build();

                                                            ctx.getSource().sendMessage(Component.text("Updated resource pack!"));

                                                            this.save();

                                                            for (Player player : this.proxy.getAllPlayers()) {
                                                                player.sendResourcePackOffer(this.info);
                                                            }

                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                        )
                        )
        ));
    }

    /*@Subscribe
    public void onPlayerGetOtherResourcePack(ServerResourcePackSendEvent event) {
        if (this.info == null)
            return;

        // force our resource pack
        event.setProvidedResourcePack(this.info);
    }*/

    @Subscribe
    public void onPlayerJoin(ServerPostConnectEvent event) {
        if (this.info == null)
            return;

        if (event.getPreviousServer() != null)
            return;

        event.getPlayer().sendResourcePackOffer(this.info);
    }

    private void save() {
        var configPath = this.dataDir.resolve("config.json");

        if (!this.dataDir.toFile().exists()) {
            this.dataDir.toFile().mkdirs();
        }

        try {
            if (!configPath.toFile().exists()) {
                configPath.toFile().createNewFile();
            }

            var json = new JsonObject();
            json.addProperty("url", this.info.getUrl());
            json.addProperty("hash", HexFormat.of().formatHex(this.info.getHash()));
            json.addProperty("prompt", MiniMessage.miniMessage().serialize(this.info.getPrompt()));

            Files.writeString(configPath, gson.toJson(json), StandardOpenOption.WRITE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void load() {
        var configPath = this.dataDir.resolve("config.json");

        if (!this.dataDir.toFile().exists()) {
            this.dataDir.toFile().mkdirs();
        }

        try {
            if (!configPath.toFile().exists()) {
                configPath.toFile().createNewFile();

                var empty = new JsonObject();
                empty.addProperty("url", "");
                empty.addProperty("hash", "");
                empty.addProperty("prompt", "This server requires you to install this resource pack!");

                Files.writeString(configPath, gson.toJson(empty), StandardOpenOption.WRITE);
                this.logger.warn("Config not set, proxy will not send resource packs!");

                return;
            }

            var json = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();

            var url = json.get("url").getAsString();
            var hash = json.get("hash").getAsString();
            var prompt = MiniMessage.miniMessage().deserialize(json.get("prompt").getAsString());

            var builder = proxy.createResourcePackBuilder(url)
                    .setShouldForce(true)
                    .setPrompt(prompt);

            if (!hash.isEmpty()) {
                builder.setHash(HexFormat.of().parseHex(hash));
            }

            this.info = builder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
