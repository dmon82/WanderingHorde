package com.pveplands.wanderinghorde;

import com.wurmonline.math.TilePos;
import com.wurmonline.math.Vector2f;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureTemplate;
import com.wurmonline.server.creatures.CreatureTemplateFactory;
import com.wurmonline.server.creatures.CreatureTypes;
import com.wurmonline.server.creatures.ai.PathTile;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.sounds.SoundPlayer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

public class Horde {
    private static int nextId = 0;
    protected static final Member[] emptyMembers = new Member[0];
    static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    
    Waypoints waypoints;
    Member anchorman;
    List<Member> satellites;
    Player creator;
    Date created;
    int id;
    int size;
    int[] templates;
    CreatureTemplate[] creatureTemplates;
    String[] sounds;
    boolean campfires = false;
    
    protected boolean spawned = false;
    protected boolean moving = false;
    protected boolean destroyed = false;
    protected boolean scattered = false;
    
    long lastMovement = System.currentTimeMillis();
    long lastSound = System.currentTimeMillis();
    
    long reachedWaypoint = Long.MAX_VALUE;
    long teleportTimeout = 120000L;
    
    /**
     * Whether or not to respawn killed creatures and insert into the horde.
     */
    boolean replenish = false;
    
    /**
     * How many creatures to respawn. A value of 0 will completely replenish
     * the horde.
     */
    long replenishQuantity = 0L;
    
    /**
     * Interval to respawn creatures at.
     */
    long replenishInterval = 3600000L;
    
    /**
     * Last time in milliseconds when creatures respawned.
     */
    long lastReplenish = System.currentTimeMillis();
    
    /**
     * Creates a new instance of Horde.
     * @param creator Player who created the horde.
     * @param size Size of the horde (forced minimum of 5).
     * @param templates A list of at least one template Ids. The first template will exclusively be the original anchor, while the horde consists of a random collection of the additional Ids.
     */
    public Horde(Player creator, int size, int ... templates) {
        if (templates == null || templates.length == 0) {
            creator.getCommunicator().sendNormalServerMessage("No templates specified, defaulting to trolls.");
            templates = new int[] { 11, 11 };
        }
        else if (templates.length < 2) {
            templates = new int[] { templates[0], templates[0] };
        }
        
        if (size < 5) {
            creator.getCommunicator().sendNormalServerMessage("Horde size was too small, forcing to size 5.");
            size = 5;
        }
        
        this.templates = templates;
        this.creator = creator;
        this.created = new Date();
        this.size = size;
        satellites = new ArrayList<>();
        id = ++nextId;
    }
    
    /**
     * Initialises the horde's creature templates.
     * @return False if there was an exception, and you shouldn't use the horde or it was previously initialised but it's unknown whether or not there was an exception.
     */
    protected boolean init() {
        if (creatureTemplates != null)
            return false;
        
        creatureTemplates = new CreatureTemplate[templates.length];

        try {
            int index = 0;
            
            for (int templateId : templates)
                creatureTemplates[index++] = CreatureTemplateFactory.getInstance().getTemplate(templateId);
        }
        catch (Exception e) {
            WanderingHorde.logger.log(Level.SEVERE, "Failed to initialise horde.", e);
            return false;
        }
        
        return true;
    }
    
    /**
     * Plays the hit sound of a random creature in the horde. If there are no
     * satellites or anchorman, no sound is played.
     */
    public void playSound() {
        Creature creature;
        
        if (satellites.isEmpty()) {
            if (anchorman == null)
                return;
            
            creature = anchorman;
        }
        else creature = satellites.get(WanderingHorde.random.nextInt(satellites.size()));

        SoundPlayer.playSound(creature.getHitSound(), creature.getTileX(), creature.getTileY(), creature.isOnSurface(), .3f);
        creature = null;
    }
    
    /**
     * Called by the anchor creature when it dies or is destroyed, then picks
     * a new anchor from the satellites. Horde is considered destroyed when no
     * more creatures exist.
     */
    public boolean newAnchor() {
        if (satellites.isEmpty()) {
            WanderingHorde.logger.warning(String.format("Can't pick new anchor because there are no satellites left in %s.", this));
            return false;
        }

        List<Member> list = new ArrayList<>(satellites);
        list.sort((left, right) -> Float.compare(left.distanceTo(anchorman), right.distanceTo(anchorman)));
        
        // waypoint reference was the anchorman?
        boolean anchormanRef = waypoints.referenceCreature != null && waypoints.referenceCreature.equals(anchorman);

        WanderingHorde.switchAnchorman(anchorman, list.get(0));
        anchorman = list.get(0);
        satellites.remove(anchorman);
        
        // set new waypoint reference creature to new anchorman.
        if (anchormanRef) waypoints.referenceCreature = anchorman;
        
        list.clear();
        list = null;
        
        WanderingHorde.logger.info(String.format("New anchor. Closest satellite %s is new anchor for %s.", anchorman, this));

        /**
         * Finding the closest tile in the path to the new anchorman.
         */
        HordePath path = waypoints.path();
        LinkedList<PathTile> tiles = path.get().getPathTiles();
        int minDist = Integer.MAX_VALUE;
        
        for (int i = tiles.size() - 1; i >= 0; i--) {
            PathTile tile = tiles.get(i);
            
            int distance = Math.max(Math.abs(anchorman.getTileX() - tile.getTileX()), Math.abs(anchorman.getTileY() - tile.getTileY()));
            
            if (distance > minDist) {
                path.index = i;
                break;
            }
            
            minDist = distance;
        }
        
        WanderingHorde.logger.info(String.format("Found that %s is dist=%d closest to %s.", path.current(), minDist, anchorman));
        
        return true;
    }
    
    /**
     * Scatters all satellites around the waypoint.
     */
    protected void scatter() {
        /*HordePath hp = waypoints.path();
        
        for (int i = 0; i < satellites.size(); i++) {
            TilePos to = hp.scatter(Options.scatterDistance, 0);
            satellites.get(i).startPathingToTile(new PathTile(to.x, to.y, Server.surfaceMesh.getTile(to), true, 0));
        }*/
        
        WanderingHorde.logger.warning("HORDE IS SCATTERED!");
        scattered = true;
    }
    
    protected void unscatter() {
        scattered = false;
        satellites.forEach(x -> { x.scattered = false; x.atDestination = false; });
    }
    
    protected void walk() {
        if (waypoints.isDirty()) {
            WanderingHorde.logger.warning(String.format("Waypoints are dirty, can't start walking %s.", this));
            return;
        }
        
        anchorman.walkToNextWaypoint();
        satellites.forEach(x -> { x.walkToNextWaypoint(); });
    }
    
    protected void halt() {
        anchorman.brain = MemberStatus.Idle;
        satellites.forEach(x -> { x.brain = MemberStatus.Idle; });
    }
    
    /**
     * Destroys the horde, including anchorman and all satellites.
     */
    public void destroy() {
        destroyed = true;
        
        WanderingHorde.logger.info(String.format("Destroying %s.", this));
        
        while (!satellites.isEmpty())
            satellites.get(0).destroy();
        
        if (anchorman != null)
            anchorman.destroy();
        anchorman = null;
        
        if (waypoints != null)
            waypoints.dispose();
        waypoints = null;
        
        WanderingHorde.remove(this);
    }
    
    public boolean isDestroyed() {
        return destroyed;
    }

    public void poll() {
        if (destroyed)
            return;
        
        long current = System.currentTimeMillis();
        
        // Play random sound from the horde after cooldown has passed.
        if (current - lastSound >= Options.soundCooldown) {
            lastSound = current + WanderingHorde.random.nextInt(Options.soundCooldown);
            
            float chance = Options.soundChance + (this.size / 100f);
            
            // Chance to play sound.
            if (WanderingHorde.random.nextFloat() <= chance)
                playSound();
        }
        
        // Replenish/respawn killed or lost satellites.
        if (replenish && (System.currentTimeMillis() - lastReplenish >= replenishInterval)) {
            Member satellite = null;

            try {
                int count = 0;
                
                for (int i = satellites.size(); i < size; i++) {
                    CreatureTemplate template = CreatureTemplateFactory.getInstance().getTemplate(templates[WanderingHorde.random.nextInt(templates.length - 1) + 1]);
                    byte mod = (byte)(WanderingHorde.random.nextFloat() > 0.025f ? 0 : WanderingHorde.random.nextInt(11) + 1); // see C_MOD in CreatureTypes.java

                    satellite = (Member)Creature.doNewHordemember(
                        template.getTemplateId(),
                        true, 
                        getSpawnPos(anchorman.getTileX()),
                        getSpawnPos(anchorman.getTileY()),
                        WanderingHorde.random.nextFloat() * 360f,
                        0, 
                        template.getName() + " member", 
                        (byte)(WanderingHorde.random.nextBoolean() ? 0 : 1),
                        (byte)0,
                        (byte)mod, /* fierce, greenish, diseased, et cetera */
                        false,
                        (byte)0);
                    satellite.horde = this;
                    satellites.add(satellite);
                    
                    WanderingHorde.logger.info(String.format("Respawned %s for %s.", satellite.getName(), this));
                    
                    if (++count >= replenishQuantity && replenishQuantity > 0)
                        break;
                }
                
                WanderingHorde.logger.info(String.format("%d creatures have been respawned.", count));
            }
            catch (Exception e) {
                WanderingHorde.logger.log(Level.SEVERE, String.format("Error RE-spawning satellites for %s.", this), e);
                
                if (satellite != null) {
                    satellite.destroy();
                    satellite = null;
                }
            }
        }
    }
    
    /**
     * Checks if all satellites are within proximity to the end point/tile of
     * the current path to walk on. If the specified timeout has been hit,
     * fighting horde members will be left behind and kicked out of the horde,
     * and others will be teleported to help avoid deadlocks.
     * @param proximity Proximity in tiles.
     * @return True if all members are now withing proximity of the end tile.
     */
    public boolean allNearDestination(int proximity) {
        HordePath hp = waypoints.path();
        boolean value = hp.nearDestination(anchorman, proximity);
        
        for (int i = 0; value && i < satellites.size(); i++) {
            Member member = satellites.get(i);
            value = hp.nearDestination(member, proximity);
            
            if (!value) {
                float timeout = (teleportTimeout - (System.currentTimeMillis() - reachedWaypoint)) / 1000f;
                
                WanderingHorde.logger.log(WanderingHorde.devlog, String.format("SATELLITE NOT WITHIN PROXIMITY (Timeout in %.2f sec): %s IN %s", timeout, satellites.get(i), this));
                
                if (timeout <= 0f) {
                    if (member.isFighting()) {
                        WanderingHorde.logger.warning(String.format("Horde member %s is fighting while horde is waiting, but timeout was hit. It will be expelled from %s.",
                            member, this));
                        
                        satellites.remove(member);
                        WanderingHorde.remove(member);
                        member.horde = null;
                    }
                    else {
                        WanderingHorde.logger.warning(String.format("Horde member %s fell behind while horde is waiting, teleporting it to %s.", member, this));
                        member.scatterTeleport(proximity);
                    }
                    
                    // prevent iteration from aborting, continue teleporting or removing.
                    value = true;
                }
            }
        }
        
        return value;
    }
    
    /**
     * Spawns the anchorman and all satellites around it.
     * @param initiator The player/GM who spawned the horde.
     * @return FALSE if the horde could not be spawned, otherwise TRUE.
     */
    public boolean spawn(Player initiator) {
        Communicator comm = initiator.getCommunicator();
        
        if (spawned) {
            comm.sendNormalServerMessage(String.format("This horde was already spawned: %s", this));
            return false;
        }
    
        if (!waypoints.hasNext()) {
            comm.sendNormalServerMessage(String.format("This horde has no waypoints: %s", this));
            return false;
        }
        
        TilePos start = waypoints.first();
        
        try {
            CreatureTemplate template = creatureTemplates[0];
            
            // always a champion creature.
            anchorman = (Member)Creature.doNewHordemember(
                    template.getTemplateId(),
                    true, 
                    (float)((start.x << 2) + 2),
                    (float)((start.y << 2) + 2),
                    WanderingHorde.random.nextFloat() * 360f,
                    0, 
                    template.getName() + " leader", 
                    (byte)(WanderingHorde.random.nextBoolean() ? 0 : 1),
                    (byte)0,
                    (byte)CreatureTypes.C_MOD_CHAMPION,
                    false,
                    (byte)0);
            anchorman.horde = this;
            waypoints.referenceCreature = anchorman;
        }
        catch (Exception e) {
            comm.sendAlertServerMessage(String.format("Can't spawn anchorman for %s.", this));
            WanderingHorde.logger.log(Level.SEVERE, String.format("Can't spawn anchorman for %s.", this), e);
            return false;
        }

        try {
            for (int i = 0; i < size; i++) {
                CreatureTemplate template = CreatureTemplateFactory.getInstance().getTemplate(templates[WanderingHorde.random.nextInt(templates.length - 1) + 1]);
                byte mod = (byte)(WanderingHorde.random.nextFloat() > 0.025f ? 0 : WanderingHorde.random.nextInt(11) + 1); // see C_MOD in CreatureTypes.java
                
                Member satellite = (Member)Creature.doNewHordemember(
                    template.getTemplateId(),
                    true, 
                    getSpawnPos(start.x),
                    getSpawnPos(start.y),
                    WanderingHorde.random.nextFloat() * 360f,
                    0, 
                    template.getName() + " member", 
                    (byte)(WanderingHorde.random.nextBoolean() ? 0 : 1),
                    (byte)0,
                    (byte)mod, /* fierce, greenish, diseased, et cetera */
                    false,
                    (byte)0);
                satellite.horde = this;
                satellites.add(satellite);
            }
        }
        catch (Exception e) {
            comm.sendAlertServerMessage(String.format("Can't spawn all satellites for %s.", this));
            WanderingHorde.logger.log(Level.SEVERE, String.format("Error spawning satellites for %s.", this), e);
            return false;
        }
        
        comm.sendNormalServerMessage(String.format("Spawned %s.", this));
        return true;
    }
    
    /**
     * Destroys all satellites and the anchorman, and respawns them at their
     * initial location.
     * @param initiator Player initiating the respawn, must not be null.
     * @return Result of the spawn method, false if errors arose while spawning.
     */
    public boolean respawn(Player initiator) {
        WanderingHorde.logger.info(String.format("%s respawning %s.", initiator, this));
        
        satellites.forEach(x -> x.destroy());
        anchorman.destroy();
        spawned = false;
        waypoints.reset();
        
        return spawn(initiator);
    }
    
    /**
     * Calculates a random point around START 3 to 10 tiles away, and randomly
     * somewhere on the tile.
     * @param start Start coordinate.
     * @return Floating point coordinate.
     */
    public float getSpawnPos(int start) {
        int distance = Options.scatterDistance;
        
        int deviation = (WanderingHorde.random.nextInt(distance) + 1);
        
        if (WanderingHorde.random.nextBoolean())
            deviation *= -1;
        
        return ((start + deviation) << 2) + WanderingHorde.random.nextFloat() * 4;
    }
    
    public int getSatellitePos(int center) {
        return center + ((WanderingHorde.random.nextInt(Options.scatterDistance) + 1) * (WanderingHorde.random.nextBoolean() ? -1 : 1));
    }
    
    protected void createCampfires() {
        if (campfires)
            return;

        campfires = true;
        
        int count = (int)Math.round((Options.scatterDistance + 1) * 4d * Math.PI / 10d);

        Vector2f[] coords = WanderingHorde.getCircleCoordinates(anchorman.getPosX(), anchorman.getPosY(), (Options.scatterDistance + 1) << 2, count);

        for (Vector2f coord : coords) {
            try {
                Item campfire = ItemFactory.createItem(ItemList.campfire, 30f, coord.x, coord.y, anchorman.getStatus().getRotation(), 
                    anchorman.isOnSurface(), (byte)0, (byte)0, anchorman.getBridgeId(), null, (byte)0);
                campfire.setTemperature((short)30000);
            }
            catch (Exception e) {
                WanderingHorde.logger.log(Level.SEVERE, "Failed to create campfire.", e);
            }
        }
    }
    
    public Member isAnchor(Creature creature) {
        return creature.equals(anchorman) ? anchorman : null;
    }
    
    public Member isSatellite(Creature creature) {
        int index = satellites.indexOf(creature);
        
        if (index < 0)
            return null;
        
        return satellites.get(index);
    }
    
    public boolean contains(Creature creature) {
        return isAnchor(creature) != null || isSatellite(creature) != null;
    }
    
    @Override
    public String toString() {
        return String.format("Horde [id: %d, created: %s, by %s, size: %d / %d]",
            id, dateFormat.format(created), creator.getName(), satellites.size(), this.size);
    }
}
