package com.pveplands.wanderinghorde;

import com.wurmonline.math.TilePos;
import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.ai.CreatureAI;
import com.wurmonline.server.creatures.ai.CreatureAIData;
import com.wurmonline.server.creatures.ai.Path;
import com.wurmonline.server.creatures.ai.PathTile;
import java.util.LinkedList;
import java.util.logging.Level;

public class SatelliteAI extends CreatureAI {
    public SatelliteAI() {
    }

    void hold(Creature creature) {
        hold(creature, false);
        
    }
    
    void hold(Creature creature, boolean onlyWhenPathIsNull) {
        if (onlyWhenPathIsNull && creature.getStatus().getPath() != null)
            return;
        
        creature.getStatus().setPath(WanderingHorde.getEmptyPath());
    }
    
    /**
     * Poll's the movement of a satellite horde creature.
     * @param creature Creature that is polled.
     * @param lastPolled Time in milliseconds when it was last polled.
     * @return True if the creature should die or be destroyed.
     */
    @Override
    protected boolean pollMovement(Creature creature, long lastPolled) {
        if (creature.isFighting())
            return false;

        Horde horde;
        
        if ((horde = WanderingHorde.inHorde(creature)) == null) {
            WanderingHorde.logger.warning(String.format("Polling movement for satellite AI but it is not in a horde? %s", creature));
            hold(creature);
            return false;
        }

        if (System.currentTimeMillis() - horde.lastMovement < Options.satelliteMovement) {
            // Stop creature from moving randomly.
            hold(creature, true);
            return false;
        }
        
        Member satellite;
        
        if ((satellite = horde.isSatellite(creature)) == null) {
            WanderingHorde.logger.warning(String.format("Satellite is not in horde? %s, %s.", creature, horde));
            hold(creature);
            return false;
        }

        if (satellite.checkHordeCombat())
            WanderingHorde.logger.warning("SATELLITE INVOLVED IN COMBAT NOW!");
        
        Path path;
        
        if ((path = satellite.getStatus().getPath()) != null && !path.isEmpty()) {
            if (WanderingHorde.logger.isLoggable(Level.FINER))
                WanderingHorde.logger.finer(String.format("Satellite still pathing, %s.", satellite));
            
            return false;
        }
        
        HordePath hp = horde.waypoints.path();
        
switchbreak:
        switch (satellite.brain) {
            case Idle:
                hold(satellite, false);
                break;
            case WalkingToTarget:
                if (satellite.walkingTarget == null || satellite.walkingTarget.isDead()) {
                    satellite.brain = MemberStatus.WalkingToWaypoint;
                    satellite.walkingTarget = null;
                    break;
                }
                
                if (satellite.isWithinDistanceTo(satellite.walkingTarget, 4f)) {
                    hold(satellite, false);
                    satellite.setTarget(satellite.walkingTarget.getWurmId(), true);
                    satellite.attackTarget();
                }
                else {
                    int targetDistX = satellite.walkingTarget.getTileX() - satellite.getTileX() - 1;
                    int targetDistY = satellite.walkingTarget.getTileY() - satellite.getTileY() - 1;
                    boolean surface = satellite.isOnSurface();
                    int toX = satellite.getTileX() + targetDistX;
                    int toY = satellite.getTileY() + targetDistY;
                    int tile = (surface ? Server.surfaceMesh.getTile(toX, toY) : Server.caveMesh.getTile(toX, toY));
                    satellite.startPathingToTile(new PathTile(toX, toY, tile, surface, satellite.getFloorLevel()));
                }
                break;
            case WalkingToWaypoint:
                if (hp.nearDestination(creature, Options.scatterDistance)) {
                    satellite.brain = MemberStatus.Scattering;
                    satellite.scatter(Options.scatterDistance);
                    break;
                }
                
                if ((path = satellite.getStatus().getPath()) != null && !path.isEmpty())
                    break;
                
                LinkedList<PathTile> tiles = hp.get().getPathTiles();
                
                // gets a tile from the list that's 1 tile behind the anchor if possible.
                int start = Math.min(tiles.size() - 1, Math.max(0, hp.index - 1));
                
                for (int i = start; i >= 0; i--) {
                    PathTile desTile = tiles.get(i);
                    TilePos dest = TilePos.fromXY(desTile.getTileX(), desTile.getTileY());
                    
                    if (satellite.isWithinTileDistanceTo(dest.x, dest.y, 0, Options.satelliteAdvance)) {
                        if (!satellite.walkDeviated(desTile.getTileX(), desTile.getTileY(), desTile.getTile(), desTile.isOnSurface(), desTile.getFloorLevel()))
                            hold(satellite, false);
                        
                        break switchbreak;
                    }
                }
                
                if (satellite.isWithinTileDistanceTo(tiles.getFirst().getTileX(), tiles.getFirst().getTileY(), 0, 0)) {
                    hold(satellite, false);
                    break;
                }
                
                satellite.startPathingToTile(tiles.getFirst());
                break;
            case Scattering:
                if ((path = satellite.getStatus().getPath()) != null && !path.isEmpty())
                    break;
                
                // Satellite at scatter point, hold movement.
                if (satellite.isWithinTileDistanceTo(satellite.scatterdest.getTileX(), satellite.scatterdest.getTileY(), 0, 0)) {
                    satellite.brain = MemberStatus.Scattered;
                    
                    satellite.rotateRandom(satellite.getStatus().getRotation(), WanderingHorde.random.nextInt(300) + 1);
                    
                    float oldX = satellite.getPosX() * 10f;
                    float oldY = satellite.getPosY() * 10f;
                    float newPosX = (satellite.getTileX() << 2) + WanderingHorde.random.nextFloat() * 4f;
                    float newPosY = (satellite.getTileY() << 2) + WanderingHorde.random.nextFloat() * 4f;
                    
                    satellite.getStatus().setPositionX(newPosX);
                    satellite.getStatus().setPositionY(newPosY);
                    satellite.moved((int)(newPosX * 10f - oldX), (int)(newPosY * 10f - oldY), 0, 0, 0);
                    hold(satellite, false);
                    break;
                }
                
                satellite.startPathingToTile(satellite.scatterdest);
                break;
            case Scattered:
                hold(satellite, false);
                break;
            case WaitingOneTurn:
                hold(satellite, false);
                break;
        }

        return false;
    }

    @Override
    protected boolean pollAttack(Creature var1, long var2) {
        return false;
    }

    @Override
    protected boolean pollBreeding(Creature var1, long var2) {
        return false;
    }

    @Override
    public CreatureAIData createCreatureAIData() {
        return new SatelliteAIData();
    }

    @Override
    public void creatureCreated(Creature creature) {
        WanderingHorde.logger.info(String.format("Satellite created: %s.", creature));
    }
    
    public class SatelliteAIData extends CreatureAIData {
        private Horde horde;
        
        public SatelliteAIData() {
        }
        
        public SatelliteAIData(Horde horde) {
            setHorde(horde);
        }
        
        public final void setHorde(Horde horde) {
            this.horde = horde;
        }
    }
}
