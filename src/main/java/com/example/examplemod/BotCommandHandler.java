package com.example.examplemod;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.server.MinecraftServer;

@Mod.EventBusSubscriber(modid = OllamaCraftMod.MODID)

public class BotCommandHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotCommandHandler.class);
    private static String ollamaUrl = "http://localhost:11434";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // /bot command
        dispatcher.register(
            Commands.literal("bot")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        CommandSourceStack source = context.getSource();
                        MinecraftServer server = source.getServer();
                        EXECUTOR.submit(() -> {
                            String response = getOllamaResponse(message);
                            // Send the response back on the main server thread
                            server.execute(() -> {
                                source.sendSuccess(() -> Component.literal("[Bot] " + response), false);
                            });
                        });
                        return Command.SINGLE_SUCCESS;
                    })
                )
        );
        // /botconfigure command
        dispatcher.register(
            Commands.literal("botconfigure")
                .then(Commands.argument("url", StringArgumentType.greedyString())
                    .executes(context -> {
                        String url = StringArgumentType.getString(context, "url");
                        ollamaUrl = url;
                        CommandSourceStack source = context.getSource();
                        source.sendSuccess(() -> Component.literal("[Bot] Ollama URL set to: " + url), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        );
    }

    private static String getOllamaResponse(String message) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String model = "qwen3:4b";
            // Build the correct JSON for /api/chat with messages array and stream=false
            String json = String.format("{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":%s}],\"stream\":false}", model, escapeJson(message));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            // Use Gson to robustly extract the 'content' field from the 'message' object
            try {
                JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
                JsonObject messageObj = root.getAsJsonObject("message");
                if (messageObj != null && messageObj.has("content")) {
                    return messageObj.get("content").getAsString();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse Ollama response JSON", e);
            }
            // fallback: return whole response if parsing fails
            return responseBody;
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to contact Ollama server", e);
            return "[Error contacting AI server]";
        }
    }

    private static String escapeJson(String text) {
        return "\"" + text.replace("\"", "\\\"") + "\"";
    }
}
