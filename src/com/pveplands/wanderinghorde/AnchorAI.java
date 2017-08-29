package com.pveplands.wanderinghorde;

import com.wurmonline.math.TilePos;
import com.wurmonline.math.Vector2f;
import com.wurmonline.server.Server;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.ai.CreatureAI;
import com.wurmonline.server.creatures.ai.CreatureAIData;
import com.wurmonline.server.creatures.ai.Path;
import com.wurmonline.server.creatures.ai.PathTile;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.zones.Zone;
import com.wurmonline.server.zones.Zones;
import java.util.logging.Level;

public class AnchorAI extends CreatureAI {
    private void hold(Creature creature) {
        hold(creature, false);
    }
    
    private void hold(Creature creature, boolean onlyWhenPathIsNull) {
        if (onlyWhenPathIsNull && creature.getStatus().getPath() != null)
            return;
        
        creature.getStatus().setPath(WanderingHorde.getEmptyPath());
        
        if (WanderingHorde.logger.isLoggable(Level.FINE))
            WanderingHorde.logger.fine("Setting empty path for anchor.");
    }
    
    @Override
    protected boolean pollMovement(Creature creature, long lastPolled) {
        if (creature.isFighting())
            return false;
        
        Horde horde;
        
        if ((horde = WanderingHorde.inHorde(creature)) == null) {
            WanderingHorde.logger.warning(String.format("Polling movement for anchor AI but is not in horde? %s", creature));
            creature.getStatus().setPath(WanderingHorde.getEmptyPath());
            return false;
        }

        if (horde.destroyed) {
            WanderingHorde.logger.warning(String.format("Polling movement for anchor in destroyed horde: %s.", horde));
            return false;
        }
        
        horde.poll();
        
        long current = System.currentTimeMillis();
        
        if (current - horde.lastMovement < Options.anchorMovement) {
            hold(creature, true);
            return false;
        }
        
        horde.lastMovement = current;

        Member anchorman;
        
        if ((anchorman = horde.isAnchor(creature)) == null) {
            WanderingHorde.logger.warning(String.format("Anchor does not match horde anchor? %s, %s, %s", horde, horde.anchorman, creature));
            return false;
        }
        
        if (anchorman.isDead()) {
            WanderingHorde.logger.warning(String.format("Polling destroyed anchor %s in %s.", anchorman, horde));
            return false;
        }
        
        if (WanderingHorde.logger.isLoggable(Level.FINE))
            WanderingHorde.logger.fine(String.format("Polling movement for anchor in horde: %s, %s", horde, anchorman));

        if (anchorman.checkHordeCombat()) {
            WanderingHorde.logger.warning("Anchor involved in combat.");
        }
        
        Path path;
        
        if ((path = anchorman.getStatus().getPath()) != null && !path.isEmpty()) {
            if (WanderingHorde.logger.isLoggable(Level.FINE))
                WanderingHorde.logger.fine(String.format("Anchor is still pathing, skipping movement poll: %s, %s", anchorman, horde));
            
            return false;
        }
        
        HordePath hp = horde.waypoints.path();
        
switchbreak:
        switch (anchorman.brain) {
            case Idle:
                hold(anchorman, false);
                break;
            case WalkingToTarget:
                if (anchorman.walkingTarget == null || anchorman.walkingTarget.isDead()) {
                    anchorman.brain = MemberStatus.WalkingToWaypoint;
                    anchorman.walkingTarget = null;
                    break;
                }
                
                if (anchorman.isWithinDistanceTo(anchorman.walkingTarget, 4f)) {
                    hold(anchorman, false);
                    anchorman.setTarget(anchorman.walkingTarget.getWurmId(), true);
                    anchorman.attackTarget();
                }
                else {
                    int targetDistX = anchorman.walkingTarget.getTileX() - anchorman.getTileX() - 1;
                    int targetDistY = anchorman.walkingTarget.getTileY() - anchorman.getTileY() - 1;
                    boolean surface = anchorman.isOnSurface();
                    int toX = anchorman.getTileX() + targetDistX;
                    int toY = anchorman.getTileY() + targetDistY;
                    int tile = (surface ? Server.surfaceMesh.getTile(toX, toY) : Server.caveMesh.getTile(toX, toY));
                    anchorman.startPathingToTile(new PathTile(toX, toY, tile, surface, anchorman.getFloorLevel()));
                }
                break;
            case WaitingOneTurn:
                hold(anchorman, false);
                
                hp.index = 0;
                
                if (!horde.waypoints.hasNext()) {
                    WanderingHorde.logger.warning(String.format("No more waypoints for %s.", horde));
                    horde.destroy();
                    break;
                }
                
                horde.moving = true;
                horde.unscatter();
                horde.walk();
                
                WanderingHorde.logger.info(String.format("Horde moving to next waypoint at %s.", horde.waypoints.next()));
                break;
            case WaitingForSatellites:
                if (horde.allNearDestination(Options.scatterDistance)) {
                    WanderingHorde.logger.info("All satellites within scatter distance, waiting one turn.");

                    horde.reachedWaypoint = Long.MAX_VALUE;
                    
                    horde.scatter();
                    horde.moving = false;
                    horde.lastMovement = current + Options.pauseAtWaypoint;
                    hold(anchorman, false);
                    
                    anchorman.brain = MemberStatus.WaitingOneTurn;
                }

                else {
                    WanderingHorde.logger.info("Not all satellites within scatter distance");
                }
                
                break;
            case WalkingToWaypoint:
                if (WurmCalendar.isNight()) {
                    WanderingHorde.logger.warning(String.format("Anchor will camp tonight (%s, %s).", anchorman, horde));
                    anchorman.brain = MemberStatus.CampAtNight;
                    horde.satellites.forEach(x -> { x.brain = MemberStatus.GatherAroundAnchor; });
                    hold(anchorman, false);
                    break;
                }
                
                if (hp.nearDestination(anchorman, 0)) {
                    WanderingHorde.logger.info(String.format("Anchor has reached the waypoint %s. (%s, %s)", hp.end, anchorman, horde));
                    anchorman.horde.reachedWaypoint = System.currentTimeMillis();
                    anchorman.brain = MemberStatus.WaitingForSatellites;
                    hold(anchorman, false);
                    break;
                }
                
                if ((path = anchorman.getStatus().getPath()) != null && !path.isEmpty())
                    break; // is pathing.

                float closest = Float.MAX_VALUE;
                for (Member satellite : horde.satellites.toArray(Horde.emptyMembers))
                    closest = Math.min(closest, satellite.distanceTo(anchorman));
                
                if (WanderingHorde.logger.isLoggable(Level.FINER))
                    WanderingHorde.logger.finer(String.format("Closest satellite is %.2f", closest));
                
                if (closest >= Options.anchorWaitDistance) {
                    hold(anchorman, false);
                    WanderingHorde.logger.info("Anchor is waiting for closest satellite.");
                    break;
                }
                
                TilePos dest = TilePos.fromXY(hp.current().getTileX(), hp.current().getTileY());

                if (anchorman.isWithinTileDistanceTo(dest.x, dest.y, 0, 0)) {
                    WanderingHorde.logger.info(String.format("Anchorman has reached sub-point %s.", dest));
                    hp.advance(Options.anchorAdvance);
                }
                
                if (anchorman.isWithinTileDistanceTo(hp.current().getTileX(), hp.current().getTileY(), 0, 0)) {
                    WanderingHorde.logger.info("Stopping anchor to pathfind on same tile.");
                    hold(anchorman, false);
                    break;
                }
                
                anchorman.startPathingToTile(hp.current());
                break;
            case CampAtNight:
                hold(anchorman, false);
                
                if (!horde.campfires)
                    horde.createCampfires();
                
                break;
        }

        return false;
    }

    @Override
    protected boolean pollAttack(Creature creature, long lastPolled) {
        return false;
    }

    @Override
    protected boolean pollBreeding(Creature creature, long lastPolled) {
        return false;
    }

    @Override
    public CreatureAIData createCreatureAIData() {
        return new AnchorAIData();
    }

    @Override
    public void creatureCreated(Creature creature) {
        WanderingHorde.logger.info(String.format("Anchorman created: %s.", creature));
    }
    
    public class AnchorAIData extends CreatureAIData {
        private Horde horde;
        
        public AnchorAIData() {
        }
        
        public AnchorAIData(Horde horde) {
            setHorde(horde);
        }

        public Horde getHorde() {
            return horde;
        }

        public final void setHorde(Horde horde) {
            this.horde = horde;
        }
    }
}
