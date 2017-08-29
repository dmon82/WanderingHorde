package com.pveplands.wanderinghorde;

import com.wurmonline.server.creatures.ai.StaticPathFinder;

public class Options {
    /**
     * Time of seconds for the anchor creature to advanced on the horde path.
     */
    protected static int anchorMovement = 6 * 1000;
    
    /**
     * The number of tiles the anchor tries to advance each movmenent tick.
     */
    protected static int anchorAdvance = 5;
    
    /**
     * Percentage chance for the anchor to assist satellites in combat.
     */
    protected static float anchorAssistChance = 0f;
    
    /**
     * Distance for the anchor to wait, if the closest satellite is this far
     * away, or even farther.
     */
    protected static float anchorWaitDistance = 10f;
    /**
     * Time of seconds for satellites to move closer to the anchor.
     */
    protected static int satelliteMovement = 5 * 1000;
    
    /**
     * Percentage chance for a satellite to assist horde members in combat.
     */
    protected static float satelliteAssistChance = 0.2f;
    
    /**
     * The number of tiles a satellite tries to advance each movement tick.
     */
    protected static int satelliteAdvance = 5;
    
    protected static float satelliteDistance = 60f;
        
    /**
     * The number of movements between deviating by 1 X/Y tile on the path.
     */
    protected static int satelliteCoordinateDeviation = 3;
    
    protected static int maxHuntDistance = 25;
    
    /**
     * Time in milliseconds to pause at each waypoint once all horde members arrived.
     */
    protected static int pauseAtWaypoint = 60000;
    
    /**
     * Number of tiles to scatter around a waypoint.
     */
    protected static int scatterDistance = 5;
    
    /**
     * Percentage chance that a sound will be played.
     */
    protected static float soundChance = 0.15f;
    
    /**
     * Cooldown in milliseconds between sounds + random.nextInt(cooldown).
     */
    protected static int soundCooldown = 6000;
    
    public Options() {
        
    }
}
