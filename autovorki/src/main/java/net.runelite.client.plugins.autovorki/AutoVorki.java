// 
// Decompiled by Procyon v0.5.36
// 

package net.runelite.client.plugins.autovorki;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.iutils.*;
import net.runelite.client.plugins.iutils.game.Game;
import net.runelite.client.plugins.iutils.ui.Chatbox;
import net.runelite.client.plugins.iutils.ui.Equipment;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.stream.IntStream;

@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(name = "AutoVorki", description = "Kills and loots Vorkath, rebanks at Moonclan", tags = {"chas", "autovorki", "vorkath", "auto"})
@Slf4j
public class AutoVorki extends Plugin {
    static List<Integer> regions;
    boolean withdrawn;
    boolean deposited;
    boolean inInstance;
    boolean looted;
    boolean specced;
    boolean dodgeBomb;
    boolean killSpawn;
    int timeout;
    boolean startVorki;
    boolean attack;
    boolean obtainedPet;
    boolean walkToStand;
    LegacyMenuEntry targetMenu;
    AutoVorkiState state;
    AutoVorkiState lastState;
    ChatMessage message;
    WorldArea moonclanTele;
    WorldArea moonclanBank;
    WorldPoint moonclanBankTile;
    WorldArea kickedOffIsland;
    WorldArea afterBoat;
    WorldPoint beforeObstacle;
    int goodBanker;
    int badBanker;
    int torfinn;
    int obstacle;
    int vorkathRegion;
    int pool;
    int nexus;
    int kills;
    int lootValue;
    NPC vorkath;
    NPC zombSpawn;
    LocalPoint startLoc;
    LocalPoint standLoc;
    String[] excluded;
    List<String> excludedItems = new ArrayList<>();
    String[] included;
    List<String> includedItems = new ArrayList<>();
    List<TileItem> toLoot = new ArrayList<>();
    Instant botTimer;
    @Inject
    OverlayManager overlayManager;
    @Inject
    AutoVorkiConfig config;
    @Inject
    private AutoVorkiOverlay overlay;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private iUtils utils;
    @Inject
    private WalkUtils walk;
    @Inject
    InventoryUtils inv;
    @Inject
    Equipment equip;
    @Inject
    private MenuUtils menu;
    @Inject
    private MouseUtils mouse;
    @Inject
    private PlayerUtils playerUtils;
    @Inject
    private PrayerUtils prayerUtils;
    @Inject
    private ObjectUtils objectUtils;
    @Inject
    private CalculationUtils calc;
    @Inject
    BankUtils bank;
    @Inject
    private InterfaceUtils interfaceUtils;
    @Inject
    private NPCUtils npcs;
    @Inject
    private Chatbox chat;
    private Player player;
    private Rectangle bounds;
    private Rectangle prayBounds;
    private Game game;
    int acidX;
    int acidY;
    int steps;
    int safeX;

    Set<Integer> diamondBolts;
    Set<Integer> rubyBolts;

    public AutoVorki() {
        moonclanTele = new WorldArea(new WorldPoint(2106, 3912, 0), new WorldPoint(2115, 3919, 0));
        moonclanBankTile = new WorldPoint(2099, 3919, 0);
        kickedOffIsland = new WorldArea(new WorldPoint(2626, 3666, 0), new WorldPoint(2649, 3687, 0));
        afterBoat = new WorldArea(new WorldPoint(2277, 4034, 0), new WorldPoint(2279, 4036, 0));
        beforeObstacle = new WorldPoint(2272, 4052, 0);
        regions = Arrays.asList(7513, 7514, 7769, 7770, 8025, 8026);
        goodBanker = 3472;
        badBanker = 3843;
        torfinn = 10405;
        obstacle = 31990;
        vorkathRegion = 9023;
        pool = 29241;
        nexus = 33402;
        vorkath = null;
        inInstance = false;
        startVorki = false;
        looted = true;
        specced = false;
        attack = false;
        dodgeBomb = false;
        killSpawn = false;
        obtainedPet = false;
        walkToStand = false;
        kills = 0;
        lootValue = 0;
        toLoot.clear();
        botTimer = null;
        steps = 0;
        safeX = -1;
    }

    public static boolean isInPOH(Client client) {
        IntStream stream = Arrays.stream(client.getMapRegions());
        List<Integer> regions = AutoVorki.regions;
        Objects.requireNonNull(regions);
        return stream.anyMatch(regions::contains);
    }

    private void reset() {
        regions = Arrays.asList(7513, 7514, 7769, 7770, 8025, 8026);
        startVorki = false;
        withdrawn = false;
        deposited = false;
        inInstance = false;
        looted = true;
        specced = false;
        attack = false;
        dodgeBomb = false;
        killSpawn = false;
        obtainedPet = false;
        message = null;
        vorkath = null;
        zombSpawn = null;
        walkToStand = false;
        kills = 0;
        lootValue = 0;
        state = AutoVorkiState.TIMEOUT;
        lastState = state;
        toLoot.clear();
        overlayManager.remove(overlay);
        botTimer = null;
        steps = 0;
        safeX = -1;
    }

    @Provides
    AutoVorkiConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoVorkiConfig.class);
    }

    protected void startUp() {
    }

    protected void shutDown() {
        reset();
    }

    @Subscribe
    private void onNpcSpawned(NpcSpawned event) {
        if (!startVorki)
            return;

        final NPC npc = event.getNpc();

        if (npc.getName() == null) {
            return;
        }

        if (npc.getName().equals("Vorkath")) {
            vorkath = event.getNpc();
        }
        if (npc.getName().equals("Zombified Spawn")) {
            killSpawn = true;
            zombSpawn = event.getNpc();
        }
    }

    @Subscribe
    private void onNpcDespawned(NpcDespawned event) {
        if (!startVorki)
            return;

        final NPC npc = event.getNpc();

        if (npc.getName() == null) {
            return;
        }

        Widget widget = client.getWidget(10485775);
        if (widget != null) {
            prayBounds = widget.getBounds();
        }

        if (npc.getName().equals("Vorkath")) {
            vorkath = null;
        }

        if (npc.getName().equals("Zombified Spawn")) {
            zombSpawn = null;
            killSpawn = false;
            equipWeapons();
        }
    }

    @Subscribe
    private void onAnimationChanged(AnimationChanged event) {
        if (!startVorki)
            return;

        final WorldPoint loc = client.getLocalPlayer().getWorldLocation();
        final LocalPoint localLoc = LocalPoint.fromWorld(client, loc);

        final Actor actor = event.getActor();

        if (actor == player) {
            if (actor.getAnimation() == 7642 || actor.getAnimation() == 1378 || actor.getAnimation() == 7514)  {
                // bgs spec / dwh spec / claws
                specced = true;
                attack = true;
            }
            if (actor.getAnimation() == 827) { // waking vorkath
                walkToStand = true;
            }
        }

        if (vorkath != null) {
            if (actor.getAnimation() == 7957 && actor.getName().contains("Vorkath")) { // acid walk
                steps = 30;
                standLoc = new LocalPoint(vorkath.getLocalLocation().getX(), vorkath.getLocalLocation().getY() - (4 * 128) - ((config.mainhand().getRange() - 1) * 128));
                if (!player.getLocalLocation().equals(getStandLoc())) {
                    walkToStart(config.mainhand().getRange());
                }
                if (config.mainhand().getRange() > 1) {
                    if (playerUtils.isRunEnabled()) {
                        Widget widget = client.getWidget(160, 25);
                        if (widget != null) {
                            bounds = widget.getBounds();
                        }

                        targetMenu = new LegacyMenuEntry("Toggle Run", "", 1, 57, -1,
                                10485783, false);
                        utils.doInvokeMsTime(targetMenu, calc.getRandomIntBetweenRange(100, 250));
                    }
                }
                timeout = 0;
            }
        }
    }

    @Subscribe
    private void onProjectileSpawned(ProjectileSpawned event) {
        if (!startVorki)
            return;

        Projectile projectile = event.getProjectile();

        WorldPoint loc = client.getLocalPlayer().getWorldLocation();

        LocalPoint localLoc = LocalPoint.fromWorld(client, loc);

        if (projectile.getId() == 1481) {// fire bomb
            dodgeBomb = true;
        }
        if (projectile.getId() == 395) {
            int sleep = calc.getRandomIntBetweenRange(25, 200);
            if (config.useStaff() && inv.containsItem(config.staffID()))
                actionItem(config.staffID(), MenuAction.ITEM_SECOND_OPTION);
            standLoc = new LocalPoint(vorkath.getLocalLocation().getX(), vorkath.getLocalLocation().getY() - (4 * 128) - ((config.mainhand().getRange() - 1) * 128));
            if (config.invokeWalk())
                walk.walkTile(standLoc.getSceneX(), standLoc.getSceneY());
            else
                walk.sceneWalk(standLoc, 0, 0);
            killSpawn = true;
        }
    }

    @Subscribe
    private void onItemSpawned(ItemSpawned event) {
        if (!startVorki)
            return;

        if (isLootableItem(event.getItem())) {
            toLoot.add(event.getItem());
            if (config.debug())
                utils.sendGameMessage("toLoot added: " + event.getItem().getId() + ", qty: " + event.getItem().getQuantity());
        }
    }

    @Subscribe
    private void onItemDespawned(ItemDespawned event) {
        if (!startVorki)
            return;

        if (toLoot.remove(event.getItem())) {
            int value = utils.getItemPrice(event.getItem().getId(), true) * event.getItem().getQuantity();
            lootValue += value;
        }
        if (toLoot.isEmpty())
            looted = true;
    }

    @Subscribe
    private void onGameStateChanged(GameStateChanged event) {
        if (!startVorki)
            return;

        GameState gamestate = event.getGameState();
        if (gamestate == GameState.LOADING) {

        }
    }

    @Subscribe
    private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
        if (!configButtonClicked.getGroup().equalsIgnoreCase("AutoVorkiConfig")) {
            return;
        }
        if ("startVorki".equals(configButtonClicked.getKey())) {
            if (!startVorki) {
                /*if (!config.debug() && config.mainhand().getRange() > 1) {
                    utils.sendGameMessage("Range is not currently supported.");
                    return;
                }*/
                startVorki = true;
                timeout = 0;
                state = null;
                targetMenu = null;
                obtainedPet = false;
                botTimer = Instant.now();
                toLoot.clear();
                looted = true;
                lootValue = 0;
                killSpawn = false;
                walkToStand = false;
                inInstance = false;
                dodgeBomb = false;
                if (!config.useDragonBolts() && config.mainhand().getRange() > 5) {
                    diamondBolts = Set.of(ItemID.DIAMOND_BOLTS_E);
                    rubyBolts = Set.of(ItemID.RUBY_BOLTS_E);
                } else {
                    diamondBolts = Set.of(ItemID.DIAMOND_DRAGON_BOLTS_E, ItemID.DIAMOND_BOLTS_E);
                    rubyBolts = Set.of(ItemID.RUBY_DRAGON_BOLTS_E, ItemID.RUBY_BOLTS_E);
                }
                kills = 0;
                excluded = config.excludedItems().toLowerCase().split("\\s*,\\s*");
                excludedItems.clear();
                included = config.includedItems().toLowerCase().split("\\s*,\\s*");
                includedItems.clear();
                excludedItems.addAll(Arrays.asList(excluded));
                includedItems.addAll(Arrays.asList(included));
                overlayManager.add(overlay);
            } else {
                reset();
            }
        }
    }

    @Subscribe
    private void onGameTick(GameTick event) {
        if (!startVorki)
            return;

        WorldPoint loc = Objects.requireNonNull(client.getLocalPlayer()).getWorldLocation();
        LocalPoint localLoc = LocalPoint.fromWorld(client, loc);

        int pot = -1;
        int sleep = 0;
        Widget widget = null;
        Widget item = null;
        player = client.getLocalPlayer();

        if (player != null && client != null) {
            state = getState();
            if (config.debug() && state != lastState && state != AutoVorkiState.TIMEOUT) {
                utils.sendGameMessage("AutoVorkiState: " + state.toString());
            }
            if (state != AutoVorkiState.TIMEOUT)
                lastState = state;
            inInstance = isInVorkath();
            if (inInstance && standLoc != null)
                safeX = getSafeX(getStandLoc());
            if (player.isMoving() && !inInstance && timeout <= 2 && vorkath == null) {
                timeout = 1;
                return;
            }
            if (!toLoot.isEmpty())
                looted = false;
            if (toLoot.isEmpty())
                looted = true;
            switch (state) {
                case TIMEOUT:
                    if (!bank.isOpen()) {
                        playerUtils.handleRun(30, 20);
                    }
                    --timeout;
                    break;
                case FIND_BANK:
                    openBank();
                    timeout = 2;
                    break;
                case TRAVEL_BANK:
                    if (player.getWorldArea().intersectsWith(moonclanTele)) {
                        walk.sceneWalk(moonclanBankTile, 0, 0);
                        timeout = calc.getRandomIntBetweenRange(2, 8);
                    }
                    break;
                case EQUIP_SPEC_WEAPON:
                    targetMenu = new LegacyMenuEntry("Wield", "", 9, MenuAction.CC_OP_LOW_PRIORITY, 0, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(), false);
                    if (!config.invokes())
                        utils.doActionMsTime(targetMenu, inv.getWidgetItem(config.useSpec().getItemId()).getCanvasBounds(), 0);
                    else
                        utils.doInvokeMsTime(targetMenu, 0);
                    timeout = 2;
                    deposited = false;
                    break;
                case DEPOSIT_INVENTORY:
                    bank.depositAll();
                    deposited = true;
                    timeout = 3;
                    break;
                case TELE_TO_POH:
                    WidgetItem ruby = inv.getWidgetItem(rubyBolts);
                    if (ruby != null) {
                        actionItem(ruby.getId(), MenuAction.ITEM_SECOND_OPTION, 0);
                    }
                    teleToPoH();
                    break;
                case WITHDRAW_MAGIC_STAFF:
                    withdrawItem(config.staffID());
                    break;
                case WITHDRAW_MAINHAND:
                    withdrawItem(config.mainhand().getItemId());
                    break;
                case WITHDRAW_SUPER_COMBAT:
                    withdrawItem(config.superCombat().getDose4());
                    break;
                case WITHDRAW_ANTIFIRE:
                    withdrawItem(config.antifire().getDose4());
                    break;
                case WITHDRAW_ANTIVENOM:
                    withdrawItem(config.antivenom().getDose4());
                    break;
                case WITHDRAW_OFFHAND:
                    withdrawItem(config.offhand().getItemId());
                    break;
                case WITHDRAW_PRAYER_RESTORE:
                    withdrawItem(config.prayer().getDose4(), config.prayerAmount());
                    timeout = 2;
                    break;
                case WITHDRAW_RUNE_POUCH:
                    withdrawItem(bank.getBankItemWidget(ItemID.RUNE_POUCH) != null ? ItemID.RUNE_POUCH : ItemID.RUNE_POUCH_L);
                    break;
                case WITHDRAW_HOUSE_TELE:
                    if (config.houseTele().getId() == ItemID.TELEPORT_TO_HOUSE)
                        withdrawItem(config.houseTele().getId(), 5);
                    else
                        withdrawItem(config.houseTele().getId());
                    break;
                case WITHDRAW_FOOD_FILL:
                    withdrawAllItem(config.food().getId());
                    break;
                case WITHDRAW_SPEC_WEAPON:
                    withdrawItem(config.useSpec().getItemId());
                    break;
                case FINISHED_WITHDRAWING:
                    if (inv.isFull())
                        withdrawn = true;
                    break;
                case WITHDRAW_FREM_SEA_BOOTS:
                    withdrawItem(ItemID.FREMENNIK_SEA_BOOTS_4);
                    break;
                case TELE_SEA_BOOTS:
                    actionItem(ItemID.FREMENNIK_SEA_BOOTS_4, MenuAction.ITEM_THIRD_OPTION);
                    timeout = calc.getRandomIntBetweenRange(3, 6);
                    break;
                case TALK_TO_BANKER:
                    actionNPC(badBanker, MenuAction.NPC_FIRST_OPTION);
                    break;
                case DRINK_POOL:
                    if (client.getVar(Varbits.QUICK_PRAYER) == 1) {
                        widget = client.getWidget(10485775);
                        if (widget != null) {
                            prayBounds = widget.getBounds();
                        }
                        targetMenu = new LegacyMenuEntry("Deactivate", "Quick-prayers", 1, MenuAction.CC_OP.getId(), -1, widget.getId(), false);
                        sleep = calc.getRandomIntBetweenRange(25, 200);
                        if (config.invokes())
                            utils.doInvokeMsTime(targetMenu, sleep);
                        else
                            utils.doActionMsTime(targetMenu, widget.getBounds(), sleep);
                        break;
                    }
                    actionObject(pool, MenuAction.GAME_OBJECT_FIRST_OPTION);
                    timeout = 5;
                    break;
                case TELEPORT_TO_MOONCLAN:
                    int obj = config.moonClanTele().getObjectID();
                    if (config.moonClanTele() == AutoVorkiConfig.MoonClanTele.CUSTOM)
                        obj = config.cMoonClanTele();
                    actionObject(obj, MenuAction.GAME_OBJECT_FIRST_OPTION);
                    timeout = 5;
                    break;
                case UNHANDLED:
                    utils.sendGameMessage("Unhandled state - stopping");
                    timeout = 2;
                    shutDown();
                    break;
                case PROGRESS_DIALOGUE:
                    continueChat();
                    break;
                case USE_BOAT:
                    actionObject(29917, MenuAction.GAME_OBJECT_FIRST_OPTION);
                    timeout = calc.getRandomIntBetweenRange(6, 8);
                    break;
                case USE_OBSTACLE:
                    actionObject(obstacle, MenuAction.GAME_OBJECT_FIRST_OPTION);
                    if (!inInstance)
                        looted = true;
                    timeout = inInstance ? 0 : (calc.getRandomIntBetweenRange(6, 8));
                    toLoot.clear();
                    break;
                case POKE_VORKATH:
                    widget = client.getWidget(WidgetInfo.MINIMAP_SPEC_CLICKBOX);

                    if (widget != null) {
                        bounds = widget.getBounds();
                    }

                    startLoc = new LocalPoint(vorkath.getLocalLocation().getX(), vorkath.getLocalLocation().getY() - (4 * 128));
                    standLoc = getStandLoc();
                    if (!player.getLocalLocation().equals(startLoc)) {
                        walkToStart();
                        timeout = 2;
                        break;
                    }
                    actionNPC(NpcID.VORKATH_8059, MenuAction.NPC_FIRST_OPTION); // 8061
                    acidX = standLoc.getSceneX();
                    acidY = standLoc.getSceneY();
                    steps = 0;
                    timeout = 2;
                    specced = false;
                    attack = true;
                    break;
                case LOOT_VORKATH:
                    if (!toLoot.isEmpty())
                        lootItem(toLoot);
                    break;
                case SPECIAL_ATTACK:
                    widget = client.getWidget(WidgetInfo.MINIMAP_SPEC_CLICKBOX);

                    if (widget != null) {
                        bounds = widget.getBounds();
                    }

                    // !specced && config.useBGS() && client.getVar(VarPlayer.SPECIAL_ATTACK_PERCENT
                    if (equip.isEquipped(config.useSpec().getItemId())) {
                        if (client.getVar(VarPlayer.SPECIAL_ATTACK_ENABLED) == 0) {
                            targetMenu = new LegacyMenuEntry("<col=ff9040>Special Attack</col>", "", 1, MenuAction.CC_OP.getId(), -1, WidgetInfo.MINIMAP_SPEC_CLICKBOX.getId(), false);
                            if (config.invokes())
                                utils.doInvokeMsTime(targetMenu, 0);
                            else
                                utils.doActionMsTime(targetMenu, bounds.getBounds(), 0);
                            timeout = 1;
                        } else {
                            attack();
                        }
                    } else {
                        if (inv.isFull() && config.useSpec() != AutoVorkiConfig.Spec.DRAGON_WARHAMMER) {
                            // don't need inventory space for dwh
                            eatFood();
                        } else {
                            actionItem(config.useSpec().getItemId(), MenuAction.ITEM_SECOND_OPTION);
                        }
                    }
                    break;
                case ACID_WALK:
                    acidX = standLoc.getSceneX();
                    acidY = standLoc.getSceneY();

                    if (steps >= 0) {
                        if (steps == 1) {
                            actionNPC(vorkath.getId(), MenuAction.NPC_SECOND_OPTION);
                        } else if (steps == 2) {
                            walkToStart(config.mainhand().getRange());
                        } else if (steps > 2) {
                            if (steps % 2 == 0) {
                                    actionNPC(vorkath.getId(), MenuAction.NPC_SECOND_OPTION);
                            } else { // move back here
                                if (safeX == -1) {
                                    utils.sendGameMessage("Unable to find suitable walk path");
                                    teleToPoH();
                                    break;
                                }
                                if (config.invokes()) {
                                    if (client.getBoostedSkillLevel(Skill.HITPOINTS) <= config.eatAt())
                                        eatFood();
                                    if (client.getBoostedSkillLevel(Skill.PRAYER) <= config.restoreAt())
                                        drinkPrayer();
                                    else if (config.drinkAntifire() && needsAntifire())
                                        drinkAntifire();
                                    else if (needsAntivenom())
                                        drinkAntivenom();
                                    else if (needsRepot())
                                        drinkSuperCombat();
                                }
                                if (config.invokeWalk()) {
                                    walk.walkTile(safeX, getStandLoc().getSceneY() - 1);
                                } else {
                                    walk.sceneWalk(new LocalPoint(safeX * 128, getStandLoc().getY() - 128), 0, 0);
                                }
                            }
                        }
                    }
                    if (steps != 0)
                        steps--;
                    if (steps < 0)
                        steps = 0;
                    break;
                case KILL_SPAWN:
                    if (zombSpawn != null) {
                        targetMenu =  new LegacyMenuEntry("Cast", "", zombSpawn.getIndex(), MenuAction.SPELL_CAST_ON_NPC.getId(), 0, 0, false);
                        utils.oneClickCastSpell(WidgetInfo.SPELL_CRUMBLE_UNDEAD, targetMenu, zombSpawn.getConvexHull().getBounds(), 100);
                        killSpawn = false;
                        timeout = 10;
                    }
                    break;
                case DODGE_BOMB:
                    if (config.debug())
                        utils.sendGameMessage("dodging bomb");
                    assert localLoc != null;
                    if (localLoc.getX() < 6208) {
                        if (config.invokeWalk())
                            walk.walkTile(localLoc.getSceneX() + 2, localLoc.getSceneY());
                        else
                            walk.sceneWalk(new LocalPoint(localLoc.getX() + 256, localLoc.getY()), 0, 0);
                    } else {
                        if (config.invokeWalk())
                            walk.walkTile(localLoc.getSceneX() - 2, localLoc.getSceneY());
                        else
                            walk.sceneWalk(new LocalPoint(localLoc.getX() - 256, localLoc.getY()), 0, 0);
                    }
                    attack = true;
                    dodgeBomb = false;
                    timeout = calc.getRandomIntBetweenRange(0, 1);
                    break;
                case EAT_FOOD:
                    eatFood();
                    break;
                case RESTORE_PRAYER:
                    drinkPrayer();
                    break;
                case DRINK_ANTIFIRE:
                    drinkAntifire();
                    break;
                case DRINK_ANTIVENOM:
                    drinkAntivenom();
                    break;
                case DRINK_SUPER_COMBAT:
                    drinkSuperCombat();
                    break;
                case ENABLE_PRAYER:
                    widget = client.getWidget(10485775);
                    if (widget != null) {
                        prayBounds = widget.getBounds();
                    }
                    targetMenu = new LegacyMenuEntry("Activate", "Quick-prayers", 1, MenuAction.CC_OP.getId(), -1, widget.getId(), false);
                    sleep = calc.getRandomIntBetweenRange(25, 200);
                    if (config.invokes())
                        utils.doInvokeMsTime(targetMenu, sleep);
                    else
                        utils.doActionMsTime(targetMenu, widget.getBounds(), sleep);
                    break;
                case DISABLE_PRAYER:
                    widget = client.getWidget(10485775);
                    if (widget != null) {
                        prayBounds = widget.getBounds();
                    }
                    targetMenu = new LegacyMenuEntry("Deactivate", "Quick-prayers", 1, MenuAction.CC_OP.getId(), -1, widget.getId(), false);
                    sleep = calc.getRandomIntBetweenRange(25, 200);
                    if (config.invokes())
                        utils.doInvokeMsTime(targetMenu, sleep);
                    else
                        utils.doActionMsTime(targetMenu, widget.getBounds(), sleep);
                    break;
                case EQUIP_WEAPONS:
                    equipWeapons();
                    break;
                case ATTACK_VORKATH:
                    attack();
                    break;
                case EQUIP_DIAMOND_BOLTS: // equip diamonds
                    WidgetItem diamond = inv.getWidgetItem(diamondBolts);
                    if (diamond != null) {
                        actionItem(diamond.getId(), MenuAction.ITEM_SECOND_OPTION, 0);
                    }
                    attack = true;
                    break;
                case EQUIP_RUBY_BOLTS: // equip rubies
                    WidgetItem ruby = inv.getWidgetItem(rubyBolts);
                    if (ruby != null) {
                        actionItem(ruby.getId(), MenuAction.ITEM_SECOND_OPTION, 0);
                    }
                    attack = true;
                    break;
                case WITHDRAW_RUBY_BOLTS:
                    item = bank.getBankItemWidgetAnyOf(rubyBolts);
                    if (item != null) {
                        withdrawAllItem(item.getItemId());
                    }
                    timeout = 3;
                    break;
                case WITHDRAW_DIAMOND_BOLTS:
                    item = bank.getBankItemWidgetAnyOf(diamondBolts);
                    if (item != null) {
                        withdrawAllItem(item.getItemId());
                    }
                    timeout = 3;
                    break;
            }
        }
    }

    void attack() {
        if (steps == 0) {
            if (!equip.isEquipped(config.mainhand().getItemId()) && timeout <= 1 && specced) {
                actionItem(config.mainhand().getItemId(), MenuAction.ITEM_SECOND_OPTION, 0);
                attack = true;
            } else if (!equip.isEquipped(config.offhand().getItemId()) && timeout <= 1 && specced && config.offhand() != AutoVorkiConfig.Offhand.NONE) {
                actionItem(config.offhand().getItemId(), MenuAction.ITEM_SECOND_OPTION, 0);
                attack = true;
            } else {
                attack = false;
                actionNPC(NpcID.VORKATH_8061, MenuAction.NPC_SECOND_OPTION); // 8061
                timeout = 1;
            }
        }
    }


    int getSafeX(LocalPoint startLoc) {
        int safeX = -1;
        if (objectUtils.getGameObjectAtLocalPoint(new LocalPoint(startLoc.getX(), startLoc.getY())) == null
                && objectUtils.getGameObjectAtLocalPoint(new LocalPoint(startLoc.getX(), startLoc.getY() - 128)) == null) {
            safeX = startLoc.getSceneX();
        } else if (objectUtils.getGameObjectAtLocalPoint(new LocalPoint(startLoc.getX() - 128, startLoc.getY())) == null
                && objectUtils.getGameObjectAtLocalPoint(new LocalPoint(startLoc.getX() - 128, startLoc.getY() - 128)) == null) {
            safeX = startLoc.getSceneX() - 1;
        } else if (objectUtils.getGameObjectAtLocalPoint(new LocalPoint(startLoc.getX() + 128, startLoc.getY())) == null
                && objectUtils.getGameObjectAtLocalPoint(new LocalPoint(startLoc.getX() + 128, startLoc.getY() - 128)) == null) {
            safeX = startLoc.getSceneX() + 1;
        } else if (objectUtils.getGameObjectAtLocalPoint(new LocalPoint(startLoc.getX() - 256, startLoc.getY())) == null
                && objectUtils.getGameObjectAtLocalPoint(new LocalPoint(startLoc.getX() - 256, startLoc.getY() - 128)) == null) {
            safeX = startLoc.getSceneX() - 2;
        } else if (objectUtils.getGameObjectAtLocalPoint(new LocalPoint(startLoc.getX() + 256, startLoc.getY())) == null
                && objectUtils.getGameObjectAtLocalPoint(new LocalPoint(startLoc.getX() + 256, startLoc.getY() - 128)) == null) {
            safeX = startLoc.getSceneX() + 2;
        }
        return safeX;
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (!startVorki)
            return;
        if (event.getType() == ChatMessageType.CONSOLE) {
            return;
        }

        Widget widget = client.getWidget(10485775);

        if (widget != null)
        {
            prayBounds = widget.getBounds();
        }

        String prayerMessage = "Your prayers have been disabled!";
        String spawnExplode = "The spawn violently explodes, unfreezing you as it does so.";
        String unfrozenMessage = "You become unfrozen as you kill the spawn.";
        String deathMessage = "Oh dear, you are dead!";
        String killComplete = "Your Vorkath";
        String petDrop = "You have a funny feeling like you're being followed.";

        if (event.getMessage().equals(spawnExplode) || (event.getMessage().equals(unfrozenMessage))) {
            killSpawn = false;
            zombSpawn = null;
            timeout = 0;
            attack = true;
            equipWeapons();
        } else if (event.getMessage().contains(killComplete)) {
            kills++;
            equipWeapons();
            steps = 0;
            dodgeBomb = false;
            zombSpawn = null;
            killSpawn = false;
            looted = false;
            specced = false;
            timeout = 2;
            //walk.sceneWalk(startLoc, 2, calc.getRandomIntBetweenRange(25, 200));
        } else if (event.getMessage().equals(deathMessage)) {
            timeout = 2;
            utils.sendGameMessage("AutoVorki: Stopping because we died.");
            utils.sendGameMessage("AutoVorki: Last state: " + lastState.toString() + ".");
            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
            Date date = new Date();
            utils.sendGameMessage("Died at: " + format.format(date));
            reset();
            shutDown();
        } else if (event.getMessage().equals(petDrop)) {
            obtainedPet = true;
        }
    }

    boolean chatboxIsOpen() {
        return chat.chatState() == Chatbox.ChatState.NPC_CHAT || chat.chatState() == Chatbox.ChatState.PLAYER_CHAT;
    }

    private void continueChat() {
        targetMenu = null;
        if (chat.chatState() == Chatbox.ChatState.NPC_CHAT) {
            targetMenu = new LegacyMenuEntry("Continue", "", 0, MenuAction.WIDGET_TYPE_6, -1, client.getWidget(231, 5).getId(), false);
            bounds = client.getWidget(231, 5).getBounds();
        }
        if (chat.chatState() == Chatbox.ChatState.PLAYER_CHAT) {
            targetMenu = new LegacyMenuEntry("Continue", "", 0, MenuAction.WIDGET_TYPE_6, -1, client.getWidget(217, 5).getId(), false);
            bounds = client.getWidget(217, 5).getBounds();
        }
        if (!config.invokes())
            utils.doActionMsTime(targetMenu, bounds, 0);
        else
            utils.doInvokeMsTime(targetMenu, 0);
    }

    AutoVorkiState getState() {
        if (dodgeBomb && timeout > 0)
            return AutoVorkiState.DODGE_BOMB;
        if (timeout > 0) {
            return AutoVorkiState.TIMEOUT;
        }
        if (chatboxIsOpen())
            return AutoVorkiState.PROGRESS_DIALOGUE;
        if (bank.isOpen())
            return getBankState();
        return getStates();
    }

    AutoVorkiState getStates() {
        if (!inInstance) {
            if (isInPOH(client)) {
                if (client.getBoostedSkillLevel(Skill.HITPOINTS) < client.getRealSkillLevel(Skill.HITPOINTS)
                        || client.getBoostedSkillLevel(Skill.PRAYER) < client.getRealSkillLevel(Skill.PRAYER)
                        || client.getVar(VarPlayer.SPECIAL_ATTACK_PERCENT) < 1000) {
                    return AutoVorkiState.DRINK_POOL;
                }
                return AutoVorkiState.TELEPORT_TO_MOONCLAN;
            } else if (!withdrawn) {
                if (player.getWorldLocation().equals(moonclanBankTile)) {
                    deposited = false;
                    withdrawn = false;
                    return AutoVorkiState.FIND_BANK;
                }
                if (player.getWorldArea().intersectsWith(moonclanTele)) {
                    return AutoVorkiState.TRAVEL_BANK;
                }
                if (inv.containsItem(config.houseTele().getId())
                        || ((inv.containsItem(ItemID.RUNE_POUCH) || inv.containsItem(ItemID.RUNE_POUCH_L))
                        && config.houseTele().getId() == 1)) {
                    return AutoVorkiState.TELE_TO_POH;
                }
                return AutoVorkiState.TIMEOUT;
            } else {
                if (withdrawn && player.getWorldArea().intersectsWith(moonclanTele))
                    return AutoVorkiState.TRAVEL_BANK;
                if (withdrawn && player.getWorldLocation().equals(moonclanBankTile))
                    return AutoVorkiState.FIND_BANK;
                if (player.getWorldArea().intersectsWith(kickedOffIsland))
                    return AutoVorkiState.USE_BOAT;
                if (player.getWorldArea().intersectsWith(afterBoat))
                    return AutoVorkiState.USE_OBSTACLE;
            }
        } else { // is in instance
            if (vorkath != null) {
                if (walkToStand) {
                    walkToStart(config.mainhand().getRange());
                    timeout = 3;
                    walkToStand = false;
                    return AutoVorkiState.TIMEOUT;
                }
                if (client.getVar(Varbits.QUICK_PRAYER) == 1 && vorkath.getId() == NpcID.VORKATH_8059)
                    return AutoVorkiState.DISABLE_PRAYER;
                if (vorkath.getId() == NpcID.VORKATH_8059 && !looted && !toLoot.isEmpty() && !inv.isFull())
                    return AutoVorkiState.LOOT_VORKATH;
                if (vorkath.getId() == NpcID.VORKATH_8059 && !looted && !toLoot.isEmpty() && inv.isFull() && config.eatLoot())
                    return AutoVorkiState.EAT_FOOD;
                if (obtainedPet) {
                    teleToPoH();
                    shutDown();
                    return null;
                }

                if (dodgeBomb)
                    return AutoVorkiState.DODGE_BOMB;
                if (killSpawn)
                    return AutoVorkiState.KILL_SPAWN;
                if (steps > 0)
                    return AutoVorkiState.ACID_WALK;

                /* in fight logic */

                // need to heal?
                if (client.getBoostedSkillLevel(Skill.HITPOINTS) <= config.eatAt()) {
                    if (inv.containsItem(config.food().getId())) {
                        attack = true;
                        return AutoVorkiState.EAT_FOOD;
                    } else
                        return AutoVorkiState.TELE_TO_POH;
                }

                // need to restore prayer?
                if (client.getBoostedSkillLevel(Skill.PRAYER) <= config.restoreAt()) {
                    attack = true;
                    return AutoVorkiState.RESTORE_PRAYER;
                }

                // need to drink antifire?
                if (config.drinkAntifire() && needsAntifire()) {
                    attack = true;
                    return AutoVorkiState.DRINK_ANTIFIRE;
                }

                // need to drink antivenom?
                if (needsAntivenom()) {
                    attack = true;
                    return AutoVorkiState.DRINK_ANTIVENOM;
                }

                // need to reboost stats?
                if (needsRepot()) {
                    attack = true;
                    return AutoVorkiState.DRINK_SUPER_COMBAT;
                }

                // swap bolts
                if (calculateHealth(vorkath, 750) <= 265 && playerUtils.isItemEquipped(rubyBolts) && config.mainhand().getRange() > 5)
                    return AutoVorkiState.EQUIP_DIAMOND_BOLTS;
                if (calculateHealth(vorkath, 750) > 265 && playerUtils.isItemEquipped(diamondBolts) && config.mainhand().getRange() > 5)
                    return AutoVorkiState.EQUIP_RUBY_BOLTS;

                // if vorkath is WAKING
                if (vorkath.getId() == NpcID.VORKATH_8058) {
                    if (client.getVar(Varbits.QUICK_PRAYER) == 0)
                        return AutoVorkiState.ENABLE_PRAYER;
                }

                // if vorkath is AWAKE
                if (vorkath.getId() == NpcID.VORKATH_8061) {
                    if (client.getVar(Varbits.QUICK_PRAYER) == 0 && vorkath.getAnimation() != 7949)
                        return AutoVorkiState.ENABLE_PRAYER;
                    if (client.getVar(Varbits.QUICK_PRAYER) == 1 && vorkath.getAnimation() == 7949)
                        return AutoVorkiState.DISABLE_PRAYER;
                    if (specced && config.useSpec() != AutoVorkiConfig.Spec.NONE && client.getVar(VarPlayer.SPECIAL_ATTACK_PERCENT) >= 800 && calculateHealth(vorkath, 750) >= 350)
                        specced = false;
                    if (!specced && config.useSpec() != AutoVorkiConfig.Spec.NONE && client.getVar(VarPlayer.SPECIAL_ATTACK_PERCENT) >= (config.useSpec().getSpecAmt() * 10))
                        return AutoVorkiState.SPECIAL_ATTACK;
                    if (attack) {
                        if (!equip.isEquipped(config.mainhand().getItemId())
                                || (!equip.isEquipped(config.offhand().getItemId())
                                && config.offhand() != AutoVorkiConfig.Offhand.NONE))
                            return AutoVorkiState.EQUIP_WEAPONS;
                        return AutoVorkiState.ATTACK_VORKATH;
                    } else
                        return AutoVorkiState.TIMEOUT;

                }

                // if vorkath is SLEEPING
                if (vorkath.getId() == NpcID.VORKATH_8059) {
                    if (client.getVar(Varbits.QUICK_PRAYER) == 1)
                        return AutoVorkiState.DISABLE_PRAYER;
                    if (looted) { // this will always be true, but why not triple check
                        if (inv.containsItemAmount(
                                config.food().getId(), config.minFood(),
                                false, false) && getPrayRestoreDoses() >= config.minPray()) {
                            return AutoVorkiState.POKE_VORKATH;
                        } else {
                            return AutoVorkiState.TELE_TO_POH;
                        }
                    }
                }
                /* end of in fight logic */
            }
        }
        return AutoVorkiState.TIMEOUT;
    }

    int getPrayRestoreDoses() {
        int count = 0;
        count += (inv.getItemCount(config.prayer().getDose1(), false));
        count += (inv.getItemCount(config.prayer().getDose2(), false) * 2);
        count += (inv.getItemCount(config.prayer().getDose3(), false) * 3);
        count += (inv.getItemCount(config.prayer().getDose4(), false) * 4);
        if (config.debug())
            utils.sendGameMessage("current dose count: " + count);
        return count;
    }

    AutoVorkiState getBankState() {
        if (bank.isOpen()) {
            if (!deposited && !withdrawn) {
                return AutoVorkiState.DEPOSIT_INVENTORY;
            }
            if (deposited && !withdrawn) {
                if (config.useSpec() != AutoVorkiConfig.Spec.NONE
                        && inv.containsItem(config.useSpec().getItemId())
                        && !equip.isEquipped(config.useSpec().getItemId()) && !inv.isFull()) {
                    return AutoVorkiState.EQUIP_SPEC_WEAPON;
                }
                if (config.useSpec() != AutoVorkiConfig.Spec.NONE
                        && !inv.containsItem(config.useSpec().getItemId())
                        && !equip.isEquipped(config.useSpec().getItemId())) {
                    return AutoVorkiState.WITHDRAW_SPEC_WEAPON;
                }
                if (!inv.containsItem(config.mainhand().getItemId()) && !equip.isEquipped(config.mainhand().getItemId())) {
                    return AutoVorkiState.WITHDRAW_MAINHAND;
                }
                if (!inv.containsItem(config.offhand().getItemId())
                        && !equip.isEquipped(config.offhand().getItemId())
                        && config.offhand() != AutoVorkiConfig.Offhand.NONE) {
                    return AutoVorkiState.WITHDRAW_OFFHAND;
                }
                if (!inv.containsItem(config.superCombat().getDose4())) {
                    return AutoVorkiState.WITHDRAW_SUPER_COMBAT;
                }
                if (!inv.containsItem(config.antifire().getDose4())) {
                    return AutoVorkiState.WITHDRAW_ANTIFIRE;
                }
                if (!inv.containsItem(config.antivenom().getDose4()) && config.antivenom().getDose4() != ItemID.SERPENTINE_HELM) {
                    return AutoVorkiState.WITHDRAW_ANTIVENOM;
                }
                if (inv.getItemCount(config.prayer().getDose4(), false) == 0) {
                    return AutoVorkiState.WITHDRAW_PRAYER_RESTORE;
                }
                if (!inv.containsItem(ItemID.RUNE_POUCH) && !inv.containsItem(ItemID.RUNE_POUCH_L)) {
                    return AutoVorkiState.WITHDRAW_RUNE_POUCH;
                }
                if (config.houseTele() == AutoVorkiConfig.HouseTele.HOUSE_TELEPORT) {
                    if (!inv.runePouchContains(ItemID.LAW_RUNE)
                            || !inv.runePouchContains(ItemID.DUST_RUNE)
                            || !inv.runePouchContains(ItemID.CHAOS_RUNE)) {
                        utils.sendGameMessage("You do not have either: law, dust and/or chaos runes in your pouch.");
                        shutDown();
                        return AutoVorkiState.TIMEOUT;
                    }
                } else {
                    if (!inv.runePouchContains(ItemID.CHAOS_RUNE)) {
                        if (!inv.runePouchContains(ItemID.DUST_RUNE)) {
                            if (!inv.runePouchContains(ItemID.AIR_RUNE) && !inv.runePouchContains(ItemID.EARTH_RUNE)) {
                                utils.sendGameMessage("You do not have either: dust and/or chaos runes in your pouch.");
                                shutDown();
                                return AutoVorkiState.TIMEOUT;
                            }
                        }

                    }
                }
                if (config.mainhand().getRange() > 5) { // if using a crossbow
                    if (!inv.containsItem(rubyBolts) && !playerUtils.isItemEquipped(rubyBolts))
                        return AutoVorkiState.WITHDRAW_RUBY_BOLTS;
                    if (!inv.containsItem(diamondBolts) && !playerUtils.isItemEquipped(diamondBolts) && config.useDiamond())
                        return AutoVorkiState.WITHDRAW_DIAMOND_BOLTS;
                }
                if (!playerUtils.isItemEquipped(rubyBolts) && config.mainhand().getRange() > 5)
                    return AutoVorkiState.EQUIP_RUBY_BOLTS;
                if (config.useStaff() && !inv.containsItem(config.staffID()) && !equip.isEquipped(config.staffID())) {
                    return AutoVorkiState.WITHDRAW_MAGIC_STAFF;
                }
                if (!inv.containsItem(config.houseTele().getId()) && config.houseTele().getId() != 1) {
                    return AutoVorkiState.WITHDRAW_HOUSE_TELE;
                }
                if (!inv.containsItem(ItemID.FREMENNIK_SEA_BOOTS_4) && config.rellekkaTele() == AutoVorkiConfig.RellekkaTele.FREMENNIK_BOOTS_4)
                    return AutoVorkiState.WITHDRAW_FREM_SEA_BOOTS;
                if (!inv.isFull()) {
                    return AutoVorkiState.WITHDRAW_FOOD_FILL;
                }
                return AutoVorkiState.FINISHED_WITHDRAWING;
            } else if (deposited && inv.getItemCount(config.food().getId(), false) >= 4) {
                equipWeapons(false);
                return config.rellekkaTele() == AutoVorkiConfig.RellekkaTele.TALK_TO_BANKER ? AutoVorkiState.TALK_TO_BANKER : AutoVorkiState.TELE_SEA_BOOTS;
            } else {
                return AutoVorkiState.DEPOSIT_INVENTORY;
            }
        }
        return AutoVorkiState.TIMEOUT;
    }

    void eatFood() {
        actionItem(config.food().getId(), MenuAction.ITEM_FIRST_OPTION);
    }

    void equipWeapons() {
        equipWeapons(true);
    }

    void equipWeapons(boolean att) {
        if (!equip.isEquipped(config.mainhand().getItemId()) && timeout <= 1) {
            actionItem(config.mainhand().getItemId(), MenuAction.ITEM_SECOND_OPTION, 0);
            attack = att;
        }
        if (!equip.isEquipped(config.offhand().getItemId()) && timeout <= 1 && config.offhand() != AutoVorkiConfig.Offhand.NONE) {
            actionItem(config.offhand().getItemId(), MenuAction.ITEM_SECOND_OPTION, 0);
            attack = att;
        }
    }

    void drinkSuperCombat() {
        int pot = -1;
        if (inv.containsItem(config.superCombat().getDose4()))
            pot = config.superCombat().getDose4();
        if (inv.containsItem(config.superCombat().getDose3()))
            pot = config.superCombat().getDose3();
        if (inv.containsItem(config.superCombat().getDose2()))
            pot = config.superCombat().getDose2();
        if (inv.containsItem(config.superCombat().getDose1()))
            pot = config.superCombat().getDose1();
        if (pot == -1) {
            teleToPoH();
            return;
        }
        actionItem(pot, MenuAction.ITEM_FIRST_OPTION);
    }

    void drinkAntivenom() {
        int pot = -1;
        if (equip.isEquipped(ItemID.SERPENTINE_HELM))
            return;
        if (inv.containsItem(config.antivenom().getDose4()))
            pot = config.antivenom().getDose4();
        if (inv.containsItem(config.antivenom().getDose3()))
            pot = config.antivenom().getDose3();
        if (inv.containsItem(config.antivenom().getDose2()))
            pot = config.antivenom().getDose2();
        if (inv.containsItem(config.antivenom().getDose1()))
            pot = config.antivenom().getDose1();
        if (pot == -1) {
            teleToPoH();
            return;
        }
        actionItem(pot, MenuAction.ITEM_FIRST_OPTION);
    }

    void drinkAntifire() {
        int pot = -1;
        if (inv.containsItem(config.antifire().getDose4()))
            pot = config.antifire().getDose4();
        if (inv.containsItem(config.antifire().getDose3()))
            pot = config.antifire().getDose3();
        if (inv.containsItem(config.antifire().getDose2()))
            pot = config.antifire().getDose2();
        if (inv.containsItem(config.antifire().getDose1()))
            pot = config.antifire().getDose1();
        if (pot == -1) {
            teleToPoH();
            return;
        }
        actionItem(pot, MenuAction.ITEM_FIRST_OPTION);
    }

    void drinkPrayer() {
        int pot = -1;
        if (inv.containsItem(config.prayer().getDose4()))
            pot = config.prayer().getDose4();
        if (inv.containsItem(config.prayer().getDose3()))
            pot = config.prayer().getDose3();
        if (inv.containsItem(config.prayer().getDose2()))
            pot = config.prayer().getDose2();
        if (inv.containsItem(config.prayer().getDose1()))
            pot = config.prayer().getDose1();
        if (pot == -1) {
            teleToPoH();
            return;
        }
        actionItem(pot, MenuAction.ITEM_FIRST_OPTION);
    }

    private boolean needsAntifire() {
        int varbit = 0;
        if (config.antifire().name() == AutoVorkiConfig.Antifire.SUPER_ANTIFIRE.name()
                || config.antifire().name() == AutoVorkiConfig.Antifire.EXT_SUPER_ANTIFIRE.name())
            varbit = 6101;
        else
            varbit = 3981;
        return client.getVarbitValue(varbit) == 0;
    }

    private boolean needsAntivenom() {
        if (equip.isEquipped(ItemID.SERPENTINE_HELM))
            return false;
        return client.getVarpValue(VarPlayer.POISON.getId()) > 0;
    }

    private boolean needsRepot() {
        int real = client.getRealSkillLevel(config.superCombat() == AutoVorkiConfig.SuperCombat.RANGING ? Skill.RANGED : (config.superCombat() == AutoVorkiConfig.SuperCombat.DIVINE_RANGING ? Skill.RANGED : (config.superCombat() == AutoVorkiConfig.SuperCombat.DIVINE_BASTION ? Skill.RANGED : (config.superCombat() == AutoVorkiConfig.SuperCombat.BASTION ? Skill.RANGED : Skill.DEFENCE))));
        int boost = client.getBoostedSkillLevel(config.superCombat() == AutoVorkiConfig.SuperCombat.RANGING ? Skill.RANGED : (config.superCombat() == AutoVorkiConfig.SuperCombat.DIVINE_RANGING ? Skill.RANGED : (config.superCombat() == AutoVorkiConfig.SuperCombat.DIVINE_BASTION ? Skill.RANGED : (config.superCombat() == AutoVorkiConfig.SuperCombat.BASTION ? Skill.RANGED : Skill.DEFENCE))));
        int repot = config.boostLevel();
        return boost <= (real + repot);
    }

    private void openBank() {
        actionNPC(goodBanker, MenuAction.NPC_THIRD_OPTION);
    }

    private LocalPoint getStandLoc() {
        return new LocalPoint(vorkath.getLocalLocation().getX(), vorkath.getLocalLocation().getY() - (4 * 128) - ((config.mainhand().getRange() - 1) * 128));
    }

    private void walkToStart() {
        walkToStart(1);
    }

    private void walkToStart(int range) { // this is gonna be a pain
        if (vorkath != null)
            startLoc = new LocalPoint(vorkath.getLocalLocation().getX(), vorkath.getLocalLocation().getY() - (4 * 128) - ((range - 1) * 128));
        if (!config.invokeWalk())
            walk.sceneWalk(new LocalPoint(startLoc.getX(), startLoc.getY()), 0, 0);
        else
            walk.walkTile(startLoc.getSceneX(), startLoc.getSceneY());
    }

    private void withdrawItem(int id, int qty) {
        Widget item = bank.getBankItemWidget(id);
        if (item != null) {
            if (qty <= 0)
                qty = 1;
            bank.withdrawItemAmount(id, qty);
        } else {
            utils.sendGameMessage("Unable to find item: " + id);
            shutDown();
        }
    }

    private void withdrawItem(int id) {
        withdrawItem(id, 1);
    }

    private void withdrawAllItem(int id) {
        Widget item = bank.getBankItemWidget(id);
        if (item != null) {
            bank.withdrawAllItem(id);
        } else {
            utils.sendGameMessage("Unable to find item: " + id);
            shutDown();
        }
    }

    private boolean isInVorkath() {
        boolean inside = false;
        if (vorkath != null) {
            inside = vorkath.getId() != NpcID.VORKATH_8058
                    || vorkath.getAnimation() != -1;
        }
        return inside;
    }

    private int calculateHealth(NPC target, Integer maxHealth) {
        if (target == null || target.getName() == null) {
            return -1;
        }
        int healthScale = target.getHealthScale();
        int healthRatio = target.getHealthRatio();
        if (healthRatio < 0 || healthScale <= 0 || maxHealth == null) {
            return -1;
        }
        return (int) (maxHealth * healthRatio / healthScale + 0.5f);
    }

    private void teleToPoH() {
        if (config.houseTele().getId() == ItemID.CONSTRUCT_CAPET || config.houseTele().getId() == ItemID.CONSTRUCT_CAPE)
            actionItem(ItemID.CONSTRUCT_CAPET, MenuAction.ITEM_FOURTH_OPTION);
        else if (config.houseTele().getId() == ItemID.TELEPORT_TO_HOUSE)
            actionItem(ItemID.TELEPORT_TO_HOUSE, MenuAction.ITEM_FIRST_OPTION);
        else if (config.houseTele().getId() == 1) {
            Widget widget = client.getWidget(218, 29);
            if (widget != null) {
                targetMenu = new LegacyMenuEntry("Cast", "<col=00ff00>Teleport to House</col>", 1 , MenuAction.CC_OP, -1, widget.getId(), false);
                utils.doActionMsTime(targetMenu, widget.getBounds(), calc.getRandomIntBetweenRange(25, 200));
            }
        }
        timeout = calc.getRandomIntBetweenRange(6, 8);
        withdrawn = false;
        deposited = false;
        steps = 0;
        toLoot.clear();
    }

    boolean isLootableItem(TileItem item) {
        String name = client.getItemDefinition(item.getId()).getName().toLowerCase();
        int value = utils.getItemPrice(item.getId(), true) * item.getQuantity();
        if (includedItems.stream().anyMatch(name.toLowerCase()::contains))
            return true;
        if (excludedItems.stream().anyMatch(name.toLowerCase()::contains))
            return false;
        if (name.equalsIgnoreCase("superior dragon bones") && config.lootBones())
            return true;
        return value >= config.lootValue();
    }

    void lootItem(List<TileItem> itemList) {
        TileItem lootItem = this.getNearestTileItem(itemList);
        if (lootItem != null) {
            this.clientThread.invoke(() -> this.client.invokeMenuAction("", "", lootItem.getId(), MenuAction.GROUND_ITEM_THIRD_OPTION.getId(), lootItem.getTile().getSceneLocation().getX(), lootItem.getTile().getSceneLocation().getY()));
        }
    }

    private TileItem getNearestTileItem(List<TileItem> tileItems) {
        int currentDistance;
        TileItem closestTileItem = tileItems.get(0);
        int closestDistance = closestTileItem.getTile().getWorldLocation().distanceTo(player.getWorldLocation());
        for (TileItem tileItem : tileItems) {
            currentDistance = tileItem.getTile().getWorldLocation().distanceTo(player.getWorldLocation());
            if (currentDistance < closestDistance) {
                closestTileItem = tileItem;
                closestDistance = currentDistance;
            }
        }
        return closestTileItem;
    }

    private boolean actionObject(int id, MenuAction action) {
        GameObject obj = objectUtils.findNearestGameObject(id);
        if (obj != null) {
            targetMenu = new LegacyMenuEntry("", "", obj.getId(), action, obj.getSceneMinLocation().getX(), obj.getSceneMinLocation().getY(), false);
            if (!config.invokes())
                utils.doGameObjectActionMsTime(obj, action.getId(), calc.getRandomIntBetweenRange(25, 200));
            else
                utils.doInvokeMsTime(targetMenu, calc.getRandomIntBetweenRange(25, 200));
            return true;
        }
        return false;
    }

    private boolean actionItem(int id, MenuAction action, int delay) {
        if (inv.containsItem(id)) {
            WidgetItem item = inv.getWidgetItem(id);
            targetMenu = new LegacyMenuEntry("", "", item.getId(), action, item.getIndex(), WidgetInfo.INVENTORY.getId(), false);
            utils.doInvokeMsTime(targetMenu, delay);
            return true;
        }
        return false;
    }

    private boolean actionItem(int id, MenuAction action) {
        return actionItem(id, action, calc.getRandomIntBetweenRange(25, 200));
    }

    private boolean actionNPC(int id, MenuAction action, int delay) {
        NPC target = npcs.findNearestNpc(id);
        if (target != null) {
            targetMenu = new LegacyMenuEntry("", "", target.getIndex(), action, target.getIndex(), 0, false);
            if (!config.invokes())
                utils.doNpcActionMsTime(target, action.getId(), delay);
            else
                utils.doInvokeMsTime(targetMenu, delay);
            return true;
        }
        return false;
    }

    private boolean actionNPC(int id, MenuAction action) {
        return actionNPC(id, action, 0);
    }
}
