package com.pveplands.wanderinghorde;

import com.wurmonline.math.TilePos;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.CreatureBehaviour;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureTemplate;
import com.wurmonline.server.creatures.CreatureTemplateIds;
import com.wurmonline.server.creatures.ai.PathTile;
import com.wurmonline.server.zones.VolaTile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class Member extends Creature {
    protected Horde horde;
    protected boolean atDestination = false;
    protected boolean scattered = false;
    protected PathTile scatterdest;
    protected MemberStatus brain = MemberStatus.Idle;
    protected int deviationCounter = Options.satelliteCoordinateDeviation;
    protected int lastDeviationX = 0;
    protected int lastDeviationY = 0;
    protected Creature walkingTarget = null;
    
    public boolean isAnchor() {
        if (horde == null)
            return false;
        
        return horde.anchorman.equals(this);
    }
    
    public boolean isSatellite() {
        if (horde == null)
            return false;
        
        return horde.satellites.indexOf(this) != -1;
    }
    
    public boolean inHorde() {
        return horde != null;
    }
    
    public Member() throws Exception {
        super();
    }
    
    public Member(CreatureTemplate template) throws Exception {
        super(template);
    }
    
    public Member(long wurmId, Horde horde) throws Exception {
        super(wurmId);

        this.horde = horde;
    }
    
    /**
     * Gets the floating point distance between two creatures (4f = 1 tile).
     * @param creature Creature to measure the distance to.
     * @return Floating point distance (4f = 1 tile).
     */
    public float distanceTo(Creature creature) {
        return (float)Math.sqrt(
            Math.pow(Math.abs(creature.getPosX() - this.getPosX()), 2) +
            Math.pow(Math.abs(creature.getPosY() - this.getPosY()), 2));
    }
    
    @Override
    public void hunt() {
        // Disable.
    }
    
    @Override
    public void die(boolean freeDeath) {
        if (isAnchor())
            horde.newAnchor();
        else
            horde.satellites.remove(this);
        
        WanderingHorde.remove(this);
        
        super.die(freeDeath);
    }
    
    @Override
    public void destroy() {
        if (isAnchor())
            horde.newAnchor();
        else
            horde.satellites.remove(this);

        WanderingHorde.remove(this);
        
        super.destroy();
    }
    
    /**
     * Scatters randomly around the current end waypoint of the horde's path.
     * @param proximity Stay within this many tiles of the target tile.
     */
    public void scatter(int proximity) {
        TilePos dest = horde.waypoints.path().scatter(proximity, 1);
        int tile = isOnSurface() ?
            Server.surfaceMesh.getTile(dest) :
            Server.caveMesh.getTile(dest);
        
        startPathingToTile(scatterdest = new PathTile(dest.x, dest.y, tile, isOnSurface(), 0));
        
        if (WanderingHorde.logger.isLoggable(Level.FINE))
            WanderingHorde.logger.fine(String.format("Satellite scattering to %s.", dest));
    }
    
    public void scatterTeleport(int proximity) {
        TilePos dest =  horde.waypoints.path().scatter(proximity, 1);
        
        CreatureBehaviour.blinkTo(this, dest.x << 2, dest.y << 2, horde.anchorman.getLayer(), horde.anchorman.getPosZDirts(), horde.anchorman.getBridgeId(), horde.anchorman.getFloorLevel());
        WanderingHorde.getSatelliteAI().hold(this, false);
        
        if (WanderingHorde.logger.isLoggable(Level.FINE))
            WanderingHorde.logger.fine(String.format("Teleporting satellite to %s.", dest));
    }
    
    /**
     * Changes the creature state to walking to next waypoint.
     */
    protected void walkToNextWaypoint() {
        brain = MemberStatus.WalkingToWaypoint;
    }
    
    /**
     * Deviates the X and Y coordinates randomly by -1 to 1 if called N times.
     */
    protected void deviate() {
        if (++deviationCounter <= Options.satelliteCoordinateDeviation)
            return;
        
        lastDeviationX = WanderingHorde.random.nextInt(2)
            * (WanderingHorde.random.nextBoolean() ? 1 : -1);
        
        lastDeviationY = WanderingHorde.random.nextInt(2)
            * (WanderingHorde.random.nextBoolean() ? 1 : -1);
    }
    
    /**
     * Starts pathing to a tile.
     * @param x Tile X coordinate.
     * @param y Tile Y coordinate.
     * @param tile Tile data.
     * @param surface Is coordinate on surface?
     * @param floor Floor level.
     * @return True if pathing was started, false if creature was already at the target coordinate.
     */
    protected boolean walkDeviated(int x, int y, int tile, boolean surface, int floor) {
        deviate();
        
        if (getTileX() == x + lastDeviationX && getTileY() == y + lastDeviationY)
            return false;
        
        startPathingToTile(new PathTile(x + lastDeviationX, y + lastDeviationY, tile, surface, floor));
        return true;
    }
    
    protected boolean checkHordeCombat() {
        Set<VolaTile> set = getCurrentTile().getThisAndSurroundingTiles(4);
        List<Creature> creatures = new ArrayList<>();
        int count = 0;
        for (VolaTile volaTile : set) {
            for (Creature c : volaTile.getCreatures()) {
                if (getTemplate().getTemplateId() == CreatureTemplateIds.TROLL_CID && c.getTemplate().getTemplateId() == CreatureTemplateIds.DEMON_SOL_CID)
                    creatures.add(c);
                else if (getTemplate().getTemplateId() == CreatureTemplateIds.DEMON_SOL_CID && c.getTemplate().getTemplateId() == CreatureTemplateIds.TROLL_CID)
                    creatures.add(c);
                
                count++;
            }
        }

        if (!creatures.isEmpty() && walkingTarget == null) {
            walkingTarget = creatures.get(WanderingHorde.random.nextInt(creatures.size()));
            brain = MemberStatus.WalkingToTarget;
            creatures.clear();
            creatures = null;
            set.clear();
            set = null;
            return true;
        }
        
        return false;
    }
}
