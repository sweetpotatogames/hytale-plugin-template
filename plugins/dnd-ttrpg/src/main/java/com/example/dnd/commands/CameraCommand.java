package com.example.dnd.commands;

import com.example.dnd.camera.CameraManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Command to toggle camera modes.
 * Usage: /dnd camera [topdown|isometric|reset]
 */
public class CameraCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> modeArg;

    public CameraCommand() {
        super("camera", "server.commands.dnd.camera.desc");
        modeArg = withDefaultArg("mode", "Camera mode: topdown, isometric, or reset",
            ArgTypes.STRING, "topdown", "topdown");
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        String mode = context.get(modeArg);

        switch (mode.toLowerCase()) {
            case "topdown" -> {
                CameraManager.applyTopDownCamera(playerRef);
                playerRef.sendMessage(Message.raw("[D&D] Switched to top-down camera view"));
            }
            case "isometric" -> {
                CameraManager.applyIsometricCamera(playerRef);
                playerRef.sendMessage(Message.raw("[D&D] Switched to isometric camera view"));
            }
            case "reset" -> {
                CameraManager.resetCamera(playerRef);
                playerRef.sendMessage(Message.raw("[D&D] Camera reset to default"));
            }
            default -> playerRef.sendMessage(Message.raw("[D&D] Unknown mode: " + mode + ". Use: topdown, isometric, or reset"));
        }
    }
}
