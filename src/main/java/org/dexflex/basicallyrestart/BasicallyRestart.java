package org.dexflex.basicallyrestart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class BasicallyRestart implements ModInitializer {
	public static final String MOD_ID = "basicallyrestart";
	private static BasicallyRestartConfig config;

	@Override
	public void onInitialize() {
		loadConfig();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("restart")
					.requires(source -> source.hasPermissionLevel(4))
					.executes(context -> {
						ServerCommandSource source = context.getSource();
						MinecraftServer server = source.getServer();

						Path runDir = FabricLoader.getInstance().getGameDir().toAbsolutePath();
						String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
						String scriptName = os.contains("win") ? config.windowsScript : config.unixScript;
						Path scriptPath = runDir.resolve(scriptName);

						if (!Files.exists(scriptPath)) {
							source.sendError(Text.literal("Restart script not found: " + scriptPath));
							return 0;
						}

						String command = os.contains("win")
								? "cmd /c start \"\" \"" + scriptPath + "\""
								: "bash \"" + scriptPath + "\"";

						Runtime.getRuntime().addShutdownHook(new Thread(() -> {
							try {
								Runtime.getRuntime().exec(command, null, runDir.toFile());
								System.out.println("Executed restart script.");
							} catch (IOException e) {
								System.err.println("Failed to run restart script: " + e.getMessage());
							}
						}));

						source.sendFeedback(Text.literal("Server restarting..."), true);
						if (config.saveBeforeRestart) server.save(true, true, true);
						server.stop(false);
						return 1;
					}));
		});
	}

	private void loadConfig() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		Path configFile = configDir.resolve("basicallyrestart.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		try {
			Files.createDirectories(configDir);
			if (Files.notExists(configFile)) {
				config = new BasicallyRestartConfig();
				try (Writer writer = Files.newBufferedWriter(configFile)) {
					gson.toJson(config, writer);
				}
			} else {
				try (Reader reader = Files.newBufferedReader(configFile)) {
					config = gson.fromJson(reader, BasicallyRestartConfig.class);
				}
			}
		} catch (IOException e) {
			System.err.println("[BasicallyRestart] Failed to load config: " + e.getMessage());
			config = new BasicallyRestartConfig();
		}
	}
}
