package org.carpet.sgu.carpetSguAddition.mixin;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.UserCache;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mixin(value = EntityPlayerMPFake.class)
public abstract class EntityPlayerMPFakeMixin extends ServerPlayerEntity {

    public EntityPlayerMPFakeMixin(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions clientOptions) {
        super(server, world, profile, clientOptions);
    }

    @Shadow
    private static Set<String> spawning;

    @Shadow
    private static CompletableFuture<Optional<GameProfile>> fetchGameProfile(final String name) {
        return null;
    }

    @Inject(method = "createFake", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onCreateFake(String username, MinecraftServer server, Vec3d pos, double yaw, double pitch, RegistryKey<World> dimensionId, GameMode gamemode, boolean flying, CallbackInfoReturnable<Boolean> cir) {
        if (!SguSettings.betterFakePlayerProcess) {
            return;
        }
        cir.setReturnValue(betterCreateFake(username, server, pos, yaw, pitch, dimensionId, gamemode, flying));
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean betterCreateFake(String username, MinecraftServer server, Vec3d pos, double yaw, double pitch, RegistryKey<World> dimensionId, GameMode gamemode, boolean flying) {
        ServerWorld worldIn = server.getWorld(dimensionId);

        java.util.UUID offlineUuid = Uuids.getOfflinePlayerUuid(username);
        java.nio.file.Path playerDataDir = server.getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA);

        GameProfile onlineProfile = null;
        boolean onlineUserIsPresent = false;

        UserCache.setUseRemote(true);
        try {
            if (server.getUserCache() != null) {
                onlineProfile = server.getUserCache().findByName(username).orElse(null);
            }
        } catch (Exception e) {
            onlineProfile = null;
        } finally {
            UserCache.setUseRemote(server.isDedicated() && server.isOnlineMode());
        }

        if (onlineProfile != null && onlineProfile.getName().equals(username)) {
            onlineUserIsPresent = true;
        } else {
            onlineProfile = null;
        }

        java.nio.file.Path offlinePath = playerDataDir.resolve(offlineUuid.toString() + ".dat");
        boolean offlineExists = java.nio.file.Files.exists(offlinePath);

        boolean onlineExists = false;
        java.nio.file.Path onlinePath = null;
        if (onlineUserIsPresent) {
            onlinePath = playerDataDir.resolve(onlineProfile.getId().toString() + ".dat");
            onlineExists = java.nio.file.Files.exists(onlinePath);
        }

        GameProfile gameprofile = null;

        if (offlineExists && onlineExists) {
            try {
                long offlineTime = java.nio.file.Files.getLastModifiedTime(offlinePath).toMillis();
                long onlineTime = java.nio.file.Files.getLastModifiedTime(onlinePath).toMillis();
                if (offlineTime <= onlineTime) {
                    gameprofile = new GameProfile(offlineUuid, username);
                } else {
                    gameprofile = onlineProfile;
                }
            } catch (Exception e) {
                gameprofile = new GameProfile(offlineUuid, username);
            }
        } else if (offlineExists) {
            gameprofile = new GameProfile(offlineUuid, username);
        } else if (onlineExists) {
            gameprofile = onlineProfile;
        } else {
            if (onlineUserIsPresent) {
                gameprofile = onlineProfile;
            } else {
                if (!CarpetSettings.allowSpawningOfflinePlayers) {
                    return false;
                } else {
                    gameprofile = new GameProfile(offlineUuid, username);
                }
            }
        }
        GameProfile finalGP = gameprofile;

        String name = gameprofile.getName();
        spawning.add(name);

        fetchGameProfile(name).whenCompleteAsync((p, t) -> {
            spawning.remove(name);
            if (t != null) {
                return;
            }

            GameProfile current = finalGP;
            if (p.isPresent()) {
                GameProfile fetched = p.get();
                current = new GameProfile(finalGP.getId(), finalGP.getName());
                current.getProperties().putAll(fetched.getProperties());
            }

            EntityPlayerMPFake instance = EntityPlayerMPFake.respawnFake(server, worldIn, current, SyncedClientOptions.createDefault());

            instance.fixStartingPosition = () -> instance.refreshPositionAndAngles(pos.x, pos.y, pos.z, (float) yaw, (float) pitch);

            server.getPlayerManager().onPlayerConnect(new FakeClientConnection(NetworkSide.SERVERBOUND), instance, new ConnectedClientData(current, 0, instance.getClientOptions(), false));

            instance.teleport(worldIn, pos.x, pos.y, pos.z, Set.of(), (float) yaw, (float) pitch, true);
            instance.setHealth(20.0F);

            ((EntityPlayerMPFakeMixin) (Object) instance).unsetRemoved();

            instance.getAttributeInstance(EntityAttributes.STEP_HEIGHT).setBaseValue(0.6F);
            instance.changeGameMode(gamemode);

            server.getPlayerManager().sendToDimension(new EntitySetHeadYawS2CPacket(instance, (byte) (instance.getHeadYaw() * 256 / 360)), dimensionId);
            server.getPlayerManager().sendToDimension(EntityPositionSyncS2CPacket.create(instance), dimensionId);

            instance.getDataTracker().set(net.minecraft.entity.player.PlayerEntity.PLAYER_MODEL_PARTS, (byte) 0x7f);
            instance.getAbilities().allowFlying = flying;

        }, server);

        return true;
    }
}
