package me.rhys.anticheat.base.processor.impl.processors;

import com.google.common.collect.EvictingQueue;
import lombok.Getter;
import lombok.Setter;
import me.rhys.anticheat.base.event.PacketEvent;
import me.rhys.anticheat.base.processor.api.Processor;
import me.rhys.anticheat.base.processor.api.ProcessorInformation;
import me.rhys.anticheat.base.user.User;
import me.rhys.anticheat.tinyprotocol.api.Packet;
import me.rhys.anticheat.tinyprotocol.packet.in.WrappedInFlyingPacket;
import me.rhys.anticheat.tinyprotocol.packet.in.WrappedInUseEntityPacket;
import me.rhys.anticheat.tinyprotocol.packet.out.WrappedOutVelocityPacket;
import me.rhys.anticheat.util.EventTimer;
import me.rhys.anticheat.util.MathUtil;
import me.rhys.anticheat.util.PastLocation;
import me.rhys.anticheat.util.PlayerLocation;
import me.rhys.anticheat.util.block.RayTrace;
import me.rhys.anticheat.util.box.BoundingBox;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

@ProcessorInformation(name = "Combat")
@Getter
@Setter
public class CombatProcessor extends Processor {

    private EventTimer preVelocityTimer, useEntityTimer;

    private double velocityH, velocityV, velocityHNoTrans;

    private Vector velocity = new Vector(), velocityNoTrans = new Vector();

    private int cancelTicks, velocityTicks, velocityNoTransTicks;

    private short velocityID;

    private Entity lastAttackedEntity, lastLastAttackedEntity;

    private PastLocation hitboxLocations = new PastLocation();

    private PlayerLocation location;

    private List<BoundingBox> boundingBoxList = new ArrayList<>();
    private boolean insideHitbox;

    @Override
    public void onPacket(PacketEvent event) {

        switch (event.getType()) {

            case Packet.Server.RESPAWN: {
                user.getActionProcessor().add(ActionProcessor.Actions.RESPAWN);
                break;
            }

            case Packet.Client.USE_ENTITY: {
                WrappedInUseEntityPacket useEntityPacket = new WrappedInUseEntityPacket(event.getPacket(), user.getPlayer());

                if (useEntityPacket.getAction() == WrappedInUseEntityPacket.EnumEntityUseAction.ATTACK) {

                    lastLastAttackedEntity = lastAttackedEntity;
                    lastAttackedEntity = useEntityPacket.getEntity();
                    useEntityTimer.reset();

                }

                if (user.getMovementProcessor().isLastSprinting() ||
                        useEntityPacket.getAction() == WrappedInUseEntityPacket.EnumEntityUseAction.ATTACK) {
                    velocity.setX(velocity.getX() * 0.6F);
                    velocity.setZ(velocity.getZ() * 0.6F);
                }
            }

            case Packet.Client.FLYING:
            case Packet.Client.LOOK:
            case Packet.Client.POSITION_LOOK:
            case Packet.Client.POSITION: {

                velocityTicks++;
                velocityNoTransTicks++;

                if (user.getCombatProcessor().getLastAttackedEntity() != null) {
                    user.getCombatProcessor().getHitboxLocations().addLocation(user.getCombatProcessor()
                            .getLastAttackedEntity().getLocation());
                }

                location = new PlayerLocation(
                        user.getCurrentLocation().getX(), user.getCurrentLocation().getY(),
                        user.getCurrentLocation().getZ(), System.currentTimeMillis());


                Location location = user.getCurrentLocation().clone()
                        .toBukkitLocation(user.getPlayer().getWorld());

                LivingEntity livingEntity = (LivingEntity) lastAttackedEntity;

                List<PlayerLocation> pastLocation = hitboxLocations.getEstimatedLocation(event.getTimestamp(),
                        user.getConnectionProcessor().getTransPing(), 200);

                if (pastLocation.size() > 0) {

                    if (livingEntity != null && location != null) {

                        if (useEntityTimer.hasNotPassed(5)) {
                            pastLocation.forEach(loc1 -> boundingBoxList.add(MathUtil.getHitbox(livingEntity, loc1, user)));

                            location.setY(location.getY() + (user.getPlayer().isSneaking() ? 1.53 - 0.4f
                                    : user.getPlayer().getEyeHeight() - .4f));

                            RayTrace trace = new RayTrace(location.toVector(),
                                    user.getPlayer().getEyeLocation().getDirection());

                            boolean outsideHitbox = boundingBoxList.stream().noneMatch(box ->
                                    trace.intersects(box, box.getMinimum().distance(location.toVector())
                                            + 1.0, .4f));

                            insideHitbox = !outsideHitbox;

                            boundingBoxList.clear();
                            pastLocation.clear();
                        }
                    }
                }

                break;
            }


            case Packet.Server.ENTITY_VELOCITY: {
                WrappedOutVelocityPacket wrappedOutVelocityPacket = new WrappedOutVelocityPacket(event.getPacket(),
                        event.getUser().getPlayer());

                if (wrappedOutVelocityPacket.getId() == event.getUser().getPlayer().getEntityId()) {

                    this.preVelocityTimer.reset();

                    user.getActionProcessor().add(ActionProcessor.Actions.VELOCITY);

                    velocity = new Vector(wrappedOutVelocityPacket.getX(), wrappedOutVelocityPacket.getY(),
                            wrappedOutVelocityPacket.getZ());

                    velocityNoTrans = new Vector(wrappedOutVelocityPacket.getX(), wrappedOutVelocityPacket.getY(),
                            wrappedOutVelocityPacket.getZ());

                    velocityNoTransTicks = 0;

                    velocityHNoTrans = Math.hypot(velocityNoTrans.getX(), velocityNoTrans.getZ());

                }
                break;
            }
        }
    }

    @Override
    public void setupTimers(User user) {
        this.preVelocityTimer = new EventTimer(20, user);
        this.useEntityTimer = new EventTimer(20, user);
    }
}

