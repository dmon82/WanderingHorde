package com.pveplands.wanderinghorde;

import com.wurmonline.math.TilePos;
import com.wurmonline.server.creatures.Creature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class to hold waypoint coordinates and paths found using Wurm's A*.
 */
public class Waypoints {
    /**
     * What the horde does when arriving at the end of the waypoint list. The
     * default behaviour for a horde is to disappear at the destination waypoint.
     */
    public enum WaypointBehaviour {
        /**
         * The horde tries to find a path back to the first waypoint,
         * ideally the waypoints are laid out in a way so they easily loop.
         */
        Loop,
        
        /**
         * The horde turns around and traces back to the first waypoint.
         */
        BackAndForth,
        
        /**
         * The horde disappears once it arrives at the final waypoint. This is
         * the default behaviour.
         */
        Disappear,
        
        /**
         * Once at the final waypoint, the horde disappears after some time.
         */
        DelayedDisappear,
        
        /**
         * The horde will wait indefinitely at the final waypoint.
         */
        Persist
    }
    
    /**
     * A creature used as reference when pathfinding (e.g. if horde can swim).
     */
    protected Creature referenceCreature;
    
    /**
     * Waypoints the horde travels to.
     */
    private List<TilePos> points;
    
    /**
     * Every single tile between two waypoints.
     */
    private List<HordePath> paths;
    
    /**
     * Current path between two waypoints.
     */
    private int index = 0;
    
    /**
     * Behaviour of the horde at the last waypoint.
     */
    private WaypointBehaviour behaviour;
    
    /**
     * Whether or not the paths need to be rebuilt at any point (e.g. when
     * inserting a new waypoint into the waypoint list.
     */
    private boolean dirty = true;
    
    /**
     * Creates an empty waypoints collection, the default behaviour for the
     * horde is to disappear once arrived at the destination waypoint.
     */
    public Waypoints() {
        this(WaypointBehaviour.Disappear);
    }
    
    public Waypoints(Creature creature) {
        this(creature, WaypointBehaviour.Disappear);
    }
    
    public Waypoints(WaypointBehaviour behaviour) {
        this(null, behaviour);
    }
    
    public Waypoints(WaypointBehaviour behaviour, TilePos ... points) {
        this(null, behaviour, points);
    }
    
    public Waypoints(Creature creature, TilePos ... points) {
        this(creature, WaypointBehaviour.Disappear, points);
    }
    
    /**
     * Creates a new waypoints collection.
     * @param creature Reference creature used when pathfinding (e.g. if horde can swim).
     * @param behaviour Behaviour of the horde once the last waypoint is reached.
     * @param points Tile coordinates that are the waypoint coordinates.
     */
    public Waypoints(Creature creature, WaypointBehaviour behaviour, TilePos ... points) {
        this.referenceCreature = creature;
        this.points = new ArrayList<>();
        this.paths = new ArrayList<>();
        
        if (points != null && points.length > 0) {
            this.points.addAll(Arrays.asList(points));
            
            /*if (points.length > 1) {
                for (int i = 1; i < points.length; i++)
                    this.paths.add(new HordePath(referenceCreature, points[i - 1], points[i]));
                
                // last waypoint to first waypoint.
                if (behaviour.equals(WaypointBehaviour.Loop))
                    this.paths.add(new HordePath(referenceCreature, points[points.length - 1], points[0]));
                else if (behaviour.equals(WaypointBehaviour.BackAndForth)) {
                    for (int i = paths.size() - 1; i >= 0; i--)
                        this.paths.add(paths.get(i).reversed());
                }
            }*/
        }
        
        this.behaviour = behaviour;
    }
    
    public void assignReference(Creature creature) {
        referenceCreature = creature;
    }
    
    protected void dispose() {
        if (paths != null) {
            for (HordePath path : paths)
                path.dispose();
            paths.clear();
        }
        paths = null;
        
        if (points != null) points.clear();
        points = null;
        
        referenceCreature = null;
    }
    
    /**
     * @return Null or the first waypoint coordinate.
     */
    public TilePos first() {
        if (nullOrEmpty())
            return null;
        
        return points.get(0);
    }
    
    /**
     * @return Null or the last waypoint coordinate.
     */
    public TilePos last() {
        if (nullOrEmpty())
            return null;
        
        return points.get(points.size() - 1);
    }
    
    /**
     * @return Null or the current waypoint coordinate.
     */
    public TilePos peek() {
        if (nullOrEmpty() || !hasNext())
            return null;
        
        // if there's a waypoint at the next index value.
        if (index + 1 < points.size())
            return points.get(index + 1);
        
        // otherwise it'd be the first in the list.
        return first();
    }
    
    /**
     * @return Null or the HordePath to the current waypoint coordinate.
     */
    public HordePath path() {
        if (nullOrEmpty())
            return null;
        
        if (dirty)
            resetPaths();
        
        return paths.get(index);
    }
    
    /**
     * @return Null or sets the current waypoint coordinate to the next one and returns it.
     */
    public TilePos next() {
        if (!hasNext())
            return null;
        
        if (++index >= points.size()) {
            if (!loops())
                return null;
            
            index = 0;
        }
        
        return points.get(index);
    }
    
    /**
     * @return True if there are more coordinates available to go to. If this is a looping collection with waypoints, it will always return true.
     */
    public boolean hasNext() {
        if (nullOrEmpty())
            return false;

        // There's a "next waypoint" if the waypoint list is not null or empty,
        // if the waypoint behaviour loops or the horde goes back and forth, or
        // or there's another waypoint past the current index.
        return loops() || index + 1 < points.size();
    }
    
    /**
     * @return True if this waypoint style makes an endless path.
     */
    public boolean loops() {
        return behaviour.equals(WaypointBehaviour.Loop)
            || behaviour.equals(WaypointBehaviour.BackAndForth);
    }
    
    /**
     * Resets the current waypoint coordinate to the first one in the list.
     */
    public void reset() {
        index = 0;
        
        if (dirty)
            resetPathsThreaded();
    }

    /**
     * Tries to find new paths for all waypoints.
     */
    protected void resetPaths() {
        long bench = System.nanoTime();
        
        paths.clear();
        
        if (points.size() > 1) {
            for (int i = 1; i < points.size(); i++)
                this.paths.add(new HordePath(referenceCreature, points.get(i - 1), points.get(i)));

            // last waypoint to first waypoint.
            if (behaviour.equals(WaypointBehaviour.Loop))
                this.paths.add(new HordePath(referenceCreature, points.get(points.size() - 1), points.get(0)));
            // back and forth with reversed paths.
            else if (behaviour.equals(WaypointBehaviour.BackAndForth)) {
                for (int i = paths.size() - 1; i >= 0; i--) {
                    this.paths.add(paths.get(i).reversed());
                }
            }
        }
        
        double mark = (System.nanoTime() - bench) / 1000000d;
        WanderingHorde.logger.log(WanderingHorde.devlog, String.format("Calculating path for waypoint took %.2f seconds.", mark));
            
        dirty = false;
    }
    
    protected void resetPathsThreaded() {
        new Thread(() -> { resetPaths(); }).start();
    }
    
    /**
     * Finds a path from the last to the first waypoint in the list.
     */
    @Deprecated
    protected void connectLoop() {
        if (paths.size() >= points.size()) {
            WanderingHorde.logger.severe("Tried to close waypoints with loop but already has too many paths?");
            return;
        }
        
        if (behaviour.equals(WaypointBehaviour.Loop))
            paths.add(new HordePath(referenceCreature, points.get(points.size() - 1), points.get(0)));
        else if (behaviour.equals(WaypointBehaviour.BackAndForth)) {
            for (int i = paths.size() - 1; i >= 0; i--)
                this.paths.add(paths.get(i).reversed());
        }
    }
    
    /**
     * @return True if there are no waypoints.
     */
    private boolean nullOrEmpty() {
        return points == null || points.isEmpty();
    }
    
    /**
     * Adds a waypoint coordinate to the collection.
     * @param tilepos The waypoint (tile X/Y) coordinate.
     */
    public void add(TilePos tilepos) {
        if (points == null)
            points = new ArrayList();
        
        points.add(tilepos);
        dirty = true;
        /*if (points.size() > 1)
            paths.add(new HordePath(referenceCreature, points.get(points.size() - 2), points.get(points.size() - 1)));*/
    }
    
    /**
     * Inserts a waypoint at a certain index.
     * @param tilepos The waypoint (tile X/Y) coordinate.
     * @param at Index to insert it at. May throw index out of bounds if used incorrectly.
     */
    public void insert(TilePos tilepos, int at) {
        if (points == null)
            points = new ArrayList();
        
        points.add(at, tilepos);
        dirty = true;
    }
    
    /**
     * @return True if the waypoints have changed, and needs resetting, and new
     * path finding.
     */
    public boolean isDirty() {
        return dirty;
    }
}
