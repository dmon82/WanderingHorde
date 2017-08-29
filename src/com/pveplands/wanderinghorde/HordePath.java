package com.pveplands.wanderinghorde;

import com.wurmonline.math.TilePos;
import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.ai.Path;
import com.wurmonline.server.creatures.ai.PathTile;
import com.wurmonline.server.creatures.ai.StaticPathFinder;
import java.util.LinkedList;
import java.util.logging.Level;

/**
 * Utility class to hold the path of a horde to a destination.
 */
public class HordePath {
    private static StaticPathFinder pathfinder;
    protected int index = 0;

    protected Path path;
    protected TilePos start;
    protected TilePos end;
    protected Creature creature;
    
    public HordePath(TilePos start, TilePos end) {
        this(null, start, end);
    }
    
    public HordePath(Creature creature, TilePos start, TilePos end) {
        this.start = start;
        this.end = end;
        this.creature = creature;
        
        // Avoid early load in modloader.
        if (pathfinder == null)
            pathfinder = new StaticPathFinder(false);
        
        if ((this.path = findPath()) == null) {
            WanderingHorde.logger.warning(String.format("HordePath could not find a path from %s to %s!", start, end));
            path = new Path(new LinkedList<>()); // empty path.
        }
        
        WanderingHorde.logger.log(WanderingHorde.devlog, String.format("Horde path from %s to %s:", start, end));
        path.getPathTiles().forEach(x -> WanderingHorde.logger.log(WanderingHorde.devlog, String.format("X: %d, Y: %d", x.getTileX(), x.getTileY())));
    }
    
    public HordePath(Path path) {
        this.start = TilePos.fromXY(path.getFirst().getTileX(), path.getFirst().getTileY());
        this.end = TilePos.fromXY(path.getTargetTile().getTileX(), path.getTargetTile().getTileY());
        this.path = path;
    }

    /**
     * Clears collections and reference types.
     */
    protected void dispose() {
        creature = null;
        if (path != null) path.clear();
        path = null;
    }
    
    private Path findPath() {
        return findPath(creature);
    }
    
    /**
     * Tries to find a path from A to B, can use a creature as reference to
     * determine whether to cross or go around bodies of water for example,
     * or ability to open doors.
     * @param creature Reference creature the WU pathfinder uses.
     * @return Path object with path tiles to follow.
     */
    private Path findPath(Creature creature) {
        if (creature == null)
            creature = this.creature;
        
        LinkedList<PathTile> tiles = new LinkedList<>();
        
        Path path = null;
        
        try {
            int counter = 0;
            TilePos currentStart = TilePos.fromXY(start.x, start.y);
            
            while (true) {
                path = pathfinder.findPath(creature, currentStart.x, currentStart.y, end.x, end.y, true, Server.surfaceMesh.getSize());
                tiles.addAll(path.getPathTiles());
                
                if (++counter > 20) {
                    WanderingHorde.logger.warning("Can't complete path???");
                    break;
                }

                if (end.x == tiles.getLast().getTileX() && end.y == tiles.getLast().getTileY()) {
                    WanderingHorde.logger.warning("Path completed.");
                    break;
                }
                
                currentStart.x = tiles.getLast().getTileX();
                currentStart.y = tiles.getLast().getTileY();
            }
        }
        catch (Exception e) {
            WanderingHorde.logger.log(Level.SEVERE, String.format("Can't get a path from %s to %s.", start, end), e);
        }
        
        return new Path(tiles);
    }
    
    /**
     * @return Gets the Path object of this HordePath.
     */
    public Path get() {
        return path;
    }
    
    /**
     * Gets the current path but hangs back by a few tiles, used by satellites
     * so they don't overtake the anchorman.
     * @param by Number of tiles to hang back by.
     * @return Path tiles.
     */
    public Path hangback(int by) {
        // stay within bounds.
        by = Math.min(by, path.getPathTiles().size() - 1);
        
        LinkedList<PathTile> sublist = new LinkedList<>(path.getPathTiles().subList(0, path.getPathTiles().size() - 1 - by));
        
        return new Path(sublist);
    }
    
    /**
     * @param proximity Distance in tiles the creature has to be within from the path's end point.
     * @param creature Creature to test the distance to the path's end point.
     * @return True if creature is within tile distance to the end waypoint.
     */
    public boolean nearDestination(Creature creature, int proximity) {
        return creature.isWithinTileDistanceTo(end.x, end.y, 0, proximity);
    }

    /**
     * Gets a random coordinate around the end waypoint.
     * @param proximity Max distance away from the end waypoint.
     * @param minDist Min distance away from the end waypoint.
     * @return Random coordinate around the end waypoint.
     */
    public TilePos scatter(int proximity, int minDist) {
        // (5 - 1) = 0 - 3 * 1 = max(4)
        return TilePos.fromXY(
            end.x + ( (WanderingHorde.random.nextInt(proximity - minDist) + minDist) * (WanderingHorde.random.nextBoolean() ? 1 : -1) ),
            end.y + ( (WanderingHorde.random.nextInt(proximity - minDist) + minDist) * (WanderingHorde.random.nextBoolean() ? 1 : -1) ));
    }
    
    /**
     * Advances the current target tile of the horde's path.
     * @param by Number of tiles to advance the target tile by.
     * @return The new target tile of the path.
     */
    protected PathTile advance(int by) {
        index = Math.min(index + by, path.getPathTiles().size() - 1);
        
        return path.getPathTiles().get(index);
    }
    
    /**
     * @return The current target tile of the path.
     */
    protected PathTile current() {
        return path.getPathTiles().get(index);
    }
    
    /**
     * @return True if there is no path or no tiles in the path, otherwise false.
     */
    public boolean isEmpty() {
        return path == null || path.getSize() == 0;
    }
    
    /**
     * Resets the path and finds a new one using Wurm's A*.
     * @return True if a path could be found and it has been reset, false if it failed.
     */
    protected boolean reset() {
        Path updated = findPath();
        
        if (updated == null) {
            WanderingHorde.logger.info(String.format("Resetting path from %s to %s failed.", start, end));
            return false;
        }

        WanderingHorde.logger.info(String.format("Path reset from %s to %s, new path has been found.", start, end));
        this.path = updated;
        this.index = 0;
        
        return true;
    }
    
    /**
     * Takes this horde path and returns a reversed version of it. Used to
     * create the "back and forth" waypoint style.
     */
    protected HordePath reversed() {
        if (isEmpty())
            return null;
        
        LinkedList<PathTile> list = new LinkedList<>();
        for (PathTile pathTile : path.getPathTiles())
            list.add(0, pathTile);
        
        return new HordePath(new Path(list));
    }
    
    /**
     * Tries to find a new path.
     * @param dest Destination tile position.
     * @return True if a new path was found, otherwise False and the old path will be kept.
     */
    protected boolean update(Creature anchorman, TilePos dest) {
        WanderingHorde.logger.info(String.format("Trying to update horde path to new destination %s for anchorman %s.", dest, anchorman));
        
        try {
            TilePos pos = TilePos.fromXY(anchorman.getTileX(), anchorman.getTileY());
            
            Path updated = pathfinder.findPath(anchorman, pos.x, pos.y, dest.x, dest.y, true, Server.surfaceMesh.getSize());
            
            if (updated == null) {
                WanderingHorde.logger.warning(String.format("Path to new destination %s was not updated (could not find a path) for anchorman %s.", dest, anchorman));
                return false;
            }
            
            WanderingHorde.logger.info(String.format("Updated horde path to new destination %s for anchorman %s.", dest, anchorman));
            
            start = pos;
            path = updated;
            index = 0;
        }
        catch (Exception e) {
            WanderingHorde.logger.log(Level.SEVERE, String.format("Can't update horde path to new destination %s for anchorman %s.", dest, anchorman), e);
            return false;
        }
        
        return true;
    }
}
