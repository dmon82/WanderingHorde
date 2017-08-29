package com.pveplands.wanderinghorde;

import com.wurmonline.math.TilePos;
import com.wurmonline.math.Vector2f;
import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureTemplateIds;
import com.wurmonline.server.creatures.ai.CreatureAI;
import com.wurmonline.server.creatures.ai.CreatureAIData;
import com.wurmonline.server.creatures.ai.Path;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PlayerMessageListener;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

public class WanderingHorde implements WurmServerMod, PreInitable, Initable, ServerStartedListener, PlayerMessageListener {
    static final Random random = new Random();
    static final Logger logger = Logger.getLogger(WanderingHorde.class.getName() + " " + WanderingHorde.class.getPackage().getImplementationVersion());
    static final Level devlog = Level.INFO;
    
    private static List<Horde> hordes = new ArrayList<>();
    public static List<Horde> getHordes() { return hordes; }
    private static final Horde[] emptyHordes = new Horde[0];
    
    // Can't instanciate here due to early load error in modloader.
    private static Map<Long, Creature> anchors = null;//new HashMap<>();
    private static Map<Long, Creature> getAnchors() { if (anchors == null) anchors = new HashMap<>(); return anchors; }
    
    private static Map<Long, Creature> satellites = null; //new HashMap<>();
    private static Map<Long, Creature> getSatellites() { if (satellites == null) satellites = new HashMap<>(); return satellites; }
    
    static Creature[] emptyCreatures = null;// = new Creature[0];
    public static Creature[] getEmptyCreatures() {
        if (emptyCreatures == null)
            emptyCreatures = new Creature[0];
        
        return emptyCreatures;
    }
    
    private static Path emptyPath = null;
    public static Path getEmptyPath() {
        if (emptyPath == null)
            emptyPath = new Path(new LinkedList<>());
        
        return emptyPath;
    }
    
    static {
    }
    
    private static AnchorAI anchorAI = null; // new AnchorAI();
    public static AnchorAI getAnchorAI() { if (anchorAI == null) anchorAI = new AnchorAI(); return anchorAI; }
    
    private static SatelliteAI satelliteAI = null; //new SatelliteAI();
    public static SatelliteAI getSatelliteAI() { if (satelliteAI == null) satelliteAI = new SatelliteAI(); return satelliteAI; }
    
    private static long lastCleaned = System.currentTimeMillis();
    
    @Override
    public void preInit() {
        /**
         * Notes:
         * 
         * The AnchorAI and SatelliteAI are not fully functional unless we find
         * a way to return it in CreatureTemplate.getCreatureAI();
         */
        try {
            CtClass creature = HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature");
            
            /**
             * Creates a call into our mod, this happens when the server is
             * started up and creatures are loaded from the database.
             */
            // public void postLoad()
            CtMethod postLoad = creature.getMethod("postLoad", "()V");
            postLoad.insertAfter("{ com.pveplands.wanderinghorde.WanderingHorde.creatureCreated(this); }");
            logger.info("Inserted creatureCreated(Creature) call to Creature.postLoad()V.");
            
            /**
             * Creates our custom CreatureAIData, and returns it if this is a horde creature.
             * We do this, because the CreatureAI is gotten from the CreatureTemplate through
             * template.getCreatureAI() and has no reference to the actual Creature.
             */
            // public CreatureAIData getCreatureAIData()
            CtMethod getAiData = creature.getMethod("getCreatureAIData", "()Lcom/wurmonline/server/creatures/ai/CreatureAIData;");
            getAiData.insertBefore(
                "{"
                + "if (this.aiData == null) {"
                + "  this.aiData = com.pveplands.wanderinghorde.WanderingHorde.getCreatureAIData(this);"
                + "  this.aiData.setCreature(this); return this.aiData;"
                + "} "
                + "else if (this.aiData instanceof com.pveplands.wanderinghorde.AnchorAI.AnchorAIData || this.aiData instanceof com.pveplands.wanderinghorde.SatelliteAI.SatelliteAIData) {"
                + "  return this.aiData;"
                + "}"
                + "}");
            logger.info("Inserted code getCreatureAIData() in CreatureAIData class.");

            /**
             * Creates a call into your WanderingHorde.creatureCreated(Creature)
             * method, which is a method in your custom AI.
             * This can be removed of we find a good way to make the
             * CreatureTemplate.getCreatureAI() return our custom AI instead.
             * 
             * Right now it doesn't because it's a method in CreatureTemplate
             * with no reference to the Creature it belongs to.
             */
            // public static Creature doNew(int templateid, boolean createPossessions, 
            //  float aPosX, float aPosY, float aRot, int layer, String name, 
            //  byte gender, byte kingdom, byte ctype, boolean reborn, byte age)
            CtMethod doNew = creature.getMethod("doNew", "(IZFFFILjava/lang/String;BBBZB)Lcom/wurmonline/server/creatures/Creature;");
            doNew.instrument(new ExprEditor() { 
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (methodCall.getMethodName().equals("creatureCreated")) {
                        doNew.insertAt(methodCall.getLineNumber() + 1, " { com.pveplands.wanderinghorde.WanderingHorde.creatureCreated(toReturn); }");
                        logger.info(String.format("Added call to WanderingHorde.creatureCreature(Creature) in Creature.doNew()"));
                    }
                }
            });
            
            /**
             * Create a new version of the doNew method to create an instance
             * of Member instead of Creature.
             */
            CtMethod doNewHordemember = CtNewMethod.copy(doNew, "doNewHordemember", creature, null);
            creature.addMethod(doNewHordemember);
            doNewHordemember.instrument(new ExprEditor() {
                @Override
                public void edit(NewExpr expr) throws CannotCompileException {
                    if (expr.getClassName().equals("com.wurmonline.server.creatures.Creature")) {
                        expr.replace("{ $_ = new com.pveplands.wanderinghorde.Member(com.wurmonline.server.creatures.CreatureTemplateFactory.getInstance().getTemplate(templateid)); }");
                        logger.info(String.format("Replaced NewExpr in doNewHordemember at line #%d.", expr.getLineNumber()));
                    }
                }
            });
            
            /**
             * When a creature is polled, also make it poll our horde creatures.
             * This can be removed of we find a good way to make the
             * CreatureTemplate.getCreatureAI() return our custom AI instead.
             * 
             * Right now it doesn't because it's a method in CreatureTemplate
             * with no reference to the Creature it belongs to.
             */
            creature.getMethod("poll", "()Z").insertBefore("{ com.pveplands.wanderinghorde.WanderingHorde.poll(this); }");
            logger.info("Inserted call to our poll method at the top of Creature.poll().");
            
            /**
             * Increases the max hunt distance for horde creatures, so they can
             * pathfind farther, but only in the findPath method instead of
             * globally. Which would change the whole dynamic of the server.
             */
            //public Path findPath(int targetX, int targetY, @Nullable PathFinder pathfinder)
            creature.getMethod("findPath", "(IILcom/wurmonline/server/creatures/ai/PathFinder;)Lcom/wurmonline/server/creatures/ai/Path;")
                .instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall methodCall) throws CannotCompileException {
                        if (methodCall.getMethodName().equals("getMaxHuntDistance")) {
                            //methodCall.replace("{ if (this instanceof com.pveplands.wanderinghorde.Member) $_ = " + Options.maxHuntDistance + "; else $_ = $proceed(); }");
                            methodCall.replace("{ if (this instanceof com.pveplands.wanderinghorde.Member) $_ = " + Options.maxHuntDistance + "; else $_ = $proceed(); }");
                            logger.info(String.format("Modified result of call to getMaxHuntDistance at line #%d.", methodCall.getLineNumber()));
                        }
                    }
                });

            /**
             * When starting up the server, replace the instance of Creature
             * with our Member class if it's supposed to be in a horde. Horde
             * data has to be loaded before server startup.
             */
            
            //public int loadAllCreatures() throws NoSuchCreatureException {
            CtClass creatures = HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creatures");
            CtMethod loadAllCreatures = creatures.getMethod("loadAllCreatures", "()I");
            
            loadAllCreatures.instrument(new ExprEditor() {
                    @Override
                    public void edit(NewExpr expr) throws CannotCompileException {
                        if (expr.getClassName().equals("com.wurmonline.server.Creature")) {
                            loadAllCreatures.insertAt(expr.getLineNumber() + 1, "{ if (com.pveplands.wanderinghorde.WanderingHorde.isInHorde(toReturn)) toReturn = new com.pveplands.wanderinghorde.Member(toReturn.getWurmId()); }");
                            logger.info(String.format("Injected code to creature loading at line #%d.", expr.getLineNumber() + 1));
                        }
                    }
                });
            
            // TODO DEBUG REMOVE stop time from advancing (always day)
            HookManager.getInstance().getClassPool().get("com.wurmonline.server.WurmCalendar")
                .getMethod("tickSecond", "()V")
                .insertBefore("{ currentTime--; }");
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Error injecting or creating code during pre-init.", e);
        }
        
        ModifyQuestion();
    }
    
    @Override
    public void init() {
        
    }
    
    private static boolean add(Horde horde) {
        if (hordes.contains(horde))
            return false;
        
        logger.info(String.format("Adding %s.", horde));
        hordes.add(horde);
        
        logger.info(String.format("Adding anchorman %s to horde %s.", horde.anchorman, horde));
        getAnchors().put(horde.anchorman.getWurmId(), horde.anchorman);
        
        horde.satellites.stream().forEach(x -> {
            getSatellites().put(x.getWurmId(), x);
            logger.info(String.format("Adding satellite: %s.", x)); 
        });
        
        return false;
    }
    
    public static boolean remove(Horde horde) {
        if (horde.anchorman != null)
            getAnchors().remove(horde.anchorman.getWurmId());
        
        horde.satellites.stream().forEach(x -> getSatellites().remove(x.getWurmId()));
        
        return hordes.remove(horde);
    }
    
    public static boolean remove(Creature creature) {
        return getSatellites().remove(creature.getWurmId()) != null || 
            getAnchors().remove(creature.getWurmId()) != null;
    }
    
    public static void switchAnchorman(Creature oldman, Creature newman) {
        getAnchors().remove(oldman.getWurmId());
        getAnchors().put(newman.getWurmId(), newman);
        getSatellites().remove(newman.getWurmId());
    }
    
    public static void clean() {
        lastCleaned = System.currentTimeMillis() + random.nextInt(60000);
        
        for (Creature creature : getAnchors().values().toArray(getEmptyCreatures())) {
            if (inHorde(creature) == null) {
                logger.warning(String.format("CLEANUP: Removing %s from mobs list.", creature));
                getAnchors().remove(creature.getWurmId());
            }
        }
        
        for (Creature creature : getSatellites().values().toArray(getEmptyCreatures())) {
            if (inHorde(creature) == null) {
                logger.warning(String.format("CLEANUP: Removing %s from getSatellites() list.", creature));
                getSatellites().remove(creature.getWurmId());
            }
        }
    }

    public static boolean isInHorde(Creature creature) {
        long wurmId = creature.getWurmId();
        
        return getSatellites().containsKey(wurmId) ||
            getAnchors().containsKey(wurmId);
    }
    
    public static Horde inHorde(Creature creature) {
        for (Horde horde : hordes.toArray(emptyHordes)) {
            if (horde.contains(creature))
                return horde;
        }
        
        return null;
    }
    
    public static CreatureAI getCreatureAI(Creature creature) {
        if (getAnchors().containsKey(creature.getWurmId()))
            return getAnchorAI();
        
        if (getSatellites().containsKey(creature.getWurmId()))
            return getSatelliteAI();
        
        return null;
    }
    
    public static void creatureCreated(long wurmId) {
        Creature creature;
        
        if ((creature = Server.getInstance().getCreatureOrNull(wurmId)) == null)
            return;
        
        creatureCreated(creature);
    }
    
    public static void creatureCreated(Creature creature) {
        CreatureAI ai;
        
        if ((ai = getCreatureAI(creature)) != null)
            ai.creatureCreated(creature);
    }
    
    public static CreatureAIData getCreatureAIData(Creature creature) {
        if (getAnchors().containsKey(creature.getWurmId()))
            return getAnchorAI().createCreatureAIData();
        
        if (getSatellites().containsKey(creature.getWurmId()))
            return getSatelliteAI().createCreatureAIData();
        
        return null;
    }
    
    public static void pollCreature(Creature creature) {
        if (getAnchors().containsKey(creature.getWurmId()))
            getAnchorAI().pollCreature(creature, System.currentTimeMillis() - creature.getCreatureAIData().getLastPollTime());
        
        if (getSatellites().containsKey(creature.getWurmId()))
            getSatelliteAI().pollCreature(creature, System.currentTimeMillis() - creature.getCreatureAIData().getLastPollTime());
    }
    
    public static void creatureDied(Creature creature) {
        logger.severe(String.format("My creature died! %s", creature));
        
        if (getAnchors().containsKey(creature.getWurmId()))
            getAnchorAI().creatureDied(creature);
        
        if (getSatellites().containsKey(creature.getWurmId()))
            getSatelliteAI().creatureDied(creature);
    }
    
    public static void poll(Creature creature) {
        if (System.currentTimeMillis() - lastCleaned > 60000L)
            clean();
        
        if (getAnchors().containsKey(creature.getWurmId())) {
            if (creature.getCreatureAIData() == null)
                logger.info(String.format("Creature AI Data is null for ANCHOR %s.", creature));
            else
                getAnchorAI().pollCreature(creature, System.currentTimeMillis() - creature.getCreatureAIData().getLastPollTime());
        }
        
        if (getSatellites().containsKey(creature.getWurmId())) {
            if (creature.getCreatureAIData() == null)
                logger.info(String.format("Creature AI Data is null for SATELLITE %s.", creature));
            else
                getSatelliteAI().pollCreature(creature, System.currentTimeMillis() - creature.getCreatureAIData().getLastPollTime());
        }
    }
    
    private void ModifyQuestion() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.questions.Question");
            
            for (CtConstructor ctor : ctClass.getConstructors())
                ctor.setModifiers((ctor.getModifiers() & ~(Modifier.PRIVATE | Modifier.PROTECTED)) | Modifier.PUBLIC);
            
            for (CtMethod method : ctClass.getDeclaredMethods())
                if (method.getName().startsWith("getBml") || method.getName().startsWith("create"))
                    method.setModifiers((method.getModifiers() & ~(Modifier.PRIVATE | Modifier.PROTECTED)) | Modifier.PUBLIC);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Could not modify com.wurmonline.server.questions.Question.", e);
        }
    }

    @Override
    public boolean onPlayerMessage(Communicator comm, String msg) {
        if (msg.equals("wat")) {
            comm.sendNormalServerMessage("Creating a horde...");
            logger.info("Creating a horde...");
            
            try {
                Horde horde = new Horde(comm.getPlayer(), 12, 
                    CreatureTemplateIds.BISON_CID,
                    CreatureTemplateIds.BISON_CID,
                    CreatureTemplateIds.BULL_CID, 
                    CreatureTemplateIds.COW_BROWN_CID);
                
                long now = System.nanoTime();
                horde.waypoints = new Waypoints(Waypoints.WaypointBehaviour.Loop);
                horde.waypoints.add(TilePos.fromXY(437, 692));
                comm.sendNormalServerMessage(String.format("Path finding took %.2f ms", (System.nanoTime() - now) / 1000000f));
                now = System.nanoTime();
                horde.waypoints.add(TilePos.fromXY(412, 708));
                comm.sendNormalServerMessage(String.format("Path finding took %.2f ms", (System.nanoTime() - now) / 1000000f));
                now = System.nanoTime();
                horde.waypoints.add(TilePos.fromXY(380, 735));
                comm.sendNormalServerMessage(String.format("Path finding took %.2f ms", (System.nanoTime() - now) / 1000000f));
                now = System.nanoTime();
                horde.waypoints.add(TilePos.fromXY(421, 763));
                comm.sendNormalServerMessage(String.format("Path finding took %.2f ms", (System.nanoTime() - now) / 1000000f));
                now = System.nanoTime();
                //horde.waypoints.connectLoop();
                comm.sendNormalServerMessage(String.format("Path finding took %.2f ms", (System.nanoTime() - now) / 1000000f));
                now = System.nanoTime();
                
                if (!horde.init()) {
                    comm.sendAlertServerMessage("Horde could not be initialised.");
                    horde.destroy();
                }
                else {
                    horde.spawn(comm.getPlayer());
                    //horde.waypoints.resetPaths();
                    horde.waypoints.resetPathsThreaded();
                    WanderingHorde.add(horde);
                }
            }
            catch (Exception e) {
                String error = String.format("Error creating horde: %s", e.toString());
                comm.sendAlertServerMessage(error);
                logger.log(Level.SEVERE, "Error creating horde.", e);
            }
        }
        else if (msg.equals("nope")) {
            while (!hordes.isEmpty()) 
                hordes.get(0).destroy();
        }
        else if (msg.equals("walk")) {
            hordes.forEach(x -> x.walk());
        }
        else if (msg.equals("halt")) {
            hordes.forEach(x -> x.halt());
        }
        else if (msg.equals("tuna")) {
            Horde tuna = new Horde(comm.getPlayer(), 15, CreatureTemplateIds.DOLPHIN_CID);
            tuna.waypoints = new Waypoints(Waypoints.WaypointBehaviour.Loop, 
                TilePos.fromXY(392, 762),
                TilePos.fromXY(365, 762),
                TilePos.fromXY(325, 717),
                TilePos.fromXY(280, 683),
                TilePos.fromXY(240, 750),
                TilePos.fromXY(255, 830),
                TilePos.fromXY(341, 819)
            );
            
            if (!tuna.init()) {
                comm.sendAlertServerMessage("Horde could not be initialised.");
                tuna.destroy();
            }
            else {
                tuna.spawn(comm.getPlayer());
                tuna.waypoints.resetPaths();
                WanderingHorde.add(tuna);
                tuna.walk();
            }
        }
        else if (msg.equals("trolls")) {
            Horde trolls = new Horde(comm.getPlayer(), 10, CreatureTemplateIds.TROLL_CID);
            trolls.waypoints = new Waypoints(Waypoints.WaypointBehaviour.Loop);
            trolls.waypoints.add(TilePos.fromXY(492, 771));
            trolls.waypoints.add(TilePos.fromXY(492, 768));
            trolls.waypoints.connectLoop();
            if (!trolls.init()) {
                comm.sendAlertServerMessage("Horde could not be initialised.");
                trolls.destroy();
            }
            else {
                trolls.spawn(comm.getPlayer());
                WanderingHorde.add(trolls);
            }
        }
        else if (msg.equals("demons")) {
            Horde demons = new Horde(comm.getPlayer(), 20, CreatureTemplateIds.DEMON_SOL_CID);
            demons.waypoints = new Waypoints(Waypoints.WaypointBehaviour.Loop);
            demons.waypoints.add(TilePos.fromXY(475,768));
            demons.waypoints.add(TilePos.fromXY(501,771));
            demons.waypoints.connectLoop();
            if (!demons.init()) {
                comm.sendAlertServerMessage("Horde could not be initialised.");
                demons.destroy();
            }
            else {
                demons.spawn(comm.getPlayer());
                WanderingHorde.add(demons);
            }
        }
        else if (msg.equals("walkdemons")) {
            hordes.get(1).walk();
        }
        else if (msg.equals("campfires")) {
            hordes.get(0).createCampfires();
        }
        else if (msg.equals("backforth")) {
            Horde horde = new Horde(comm.getPlayer(), 10, 
                CreatureTemplateIds.HELL_HOUND_CID,
                CreatureTemplateIds.HELL_HOUND_CID,
                CreatureTemplateIds.HELL_SCORPION_CID,
                CreatureTemplateIds.HELL_HORSE_CID);
            
            if (!horde.init()) {
                comm.sendAlertServerMessage("Horde could not be initialised.");
                horde.destroy();
            }
            else {
                horde.waypoints = new Waypoints(Waypoints.WaypointBehaviour.BackAndForth);
                
                horde.waypoints.add(TilePos.fromXY(421, 763));
                horde.waypoints.add(TilePos.fromXY(437, 692));
                horde.waypoints.connectLoop();
                horde.spawn(comm.getPlayer());
                WanderingHorde.add(horde);
                horde.waypoints.resetPaths();
            }
        }
        
        return false;
    }

    /**
     * Gets a set of coordinates making a circle around a center point.
     * @param x Center X coordinate.
     * @param y Center Y coordinate.
     * @param radius Distance from the center.
     * @param count Number of coordinates .
     * @return Set of X, Y coordinates around a center point at radius distance.
     */
    public static Vector2f[] getCircleCoordinates(float x, float y, float radius, int count) {
        Vector2f[] result = new Vector2f[count];
        float degrees = 360f / count;
        float current = 0f;
        
        for (int i = 0; i < count; i++) {
            result[i] = new Vector2f(x + (float)Math.cos(current * Math.PI / 180d) * radius,
                y + (float)Math.sin(current * Math.PI / 180d) * radius);

            current += degrees;
        }
        
        return result;
    }
    
    @Override
    public void onServerStarted() {

    }
}
