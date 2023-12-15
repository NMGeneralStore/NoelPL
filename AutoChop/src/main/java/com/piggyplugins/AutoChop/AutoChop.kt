package com.piggyplugins.AutoChop

import com.example.EthanApiPlugin.Collections.*
import com.example.EthanApiPlugin.EthanApiPlugin
import com.example.InteractionApi.BankInventoryInteraction
import com.example.InteractionApi.NPCInteraction
import com.example.InteractionApi.TileObjectInteraction
import com.example.Packets.MousePackets
import com.example.Packets.WidgetPackets
import com.example.PathingTesting.PathingTesting
import com.google.inject.Inject
import com.google.inject.Provides
import com.piggyplugins.PiggyUtils.BreakHandler.ReflectBreakHandler
import net.runelite.api.Client
import net.runelite.api.GameState
import net.runelite.api.coords.WorldArea
import net.runelite.api.coords.WorldPoint
import net.runelite.api.events.GameTick
import net.runelite.client.config.ConfigManager
import net.runelite.client.eventbus.Subscribe
import net.runelite.client.plugins.Plugin
import net.runelite.client.plugins.PluginDescriptor
import net.runelite.client.ui.overlay.OverlayManager
import java.awt.Robot
import java.awt.event.KeyEvent

@PluginDescriptor(
    name = "<html><font color=\"#FF9DF9\">[PP]</font> Auto Chop </html>",
    description = "Choppy Choppy",
    tags = ["jc"],
    enabledByDefault = false
)
class AutoChop : Plugin() {
    @Inject
    private lateinit var client: Client
    @Inject
    private lateinit var autoChopConfig: AutoChopConfig
    @Inject
    private lateinit var breakHandler: ReflectBreakHandler
    @Inject
    private lateinit var autoChopOverlay: AutoChopOverlay
    @Inject
    private lateinit var overlayManager: OverlayManager

    lateinit var state: State
    private lateinit var keyboard: Robot

    private lateinit var bankingArea: WorldArea
    private lateinit var treeArea: WorldArea
    private lateinit var bankDestination: WorldPoint
    private lateinit var treeDestination: WorldPoint

    private var tickDelay = 0

    @Provides
    private fun getConfig(configManager: ConfigManager): AutoChopConfig {
        return configManager.getConfig(AutoChopConfig::class.java)
    }

    @Throws(Exception::class)
    override fun startUp() {
        keyboard = Robot()
        breakHandler.registerPlugin(this);
        breakHandler.startPlugin(this);
        changeStateTo(State.IDLE)
    }

    @Throws(Exception::class)
    override fun shutDown() {
        breakHandler.stopPlugin(this);
        breakHandler.unregisterPlugin(this);
        overlayManager.remove(autoChopOverlay)
    }

    @Subscribe
    fun onGameTick(e: GameTick) {
        if (tickDelay > 0) { // Tick delay
            tickDelay--
            return
        }

        if (autoChopConfig.displayOverlay()){ // Toggle overlay
            overlayManager.add(autoChopOverlay)
        } else {
            overlayManager.remove(autoChopOverlay)
        }

        if (breakHandler.shouldBreak(this)) { // Break handler
            breakHandler.startBreak(this)
        }

        if (client.gameState != GameState.LOGGED_IN) { // Check if logged in
            return
        }


        // Set up areas and destinations
        bankingArea = WorldArea(autoChopConfig.bankAreaXY().width, autoChopConfig.bankAreaXY().height, autoChopConfig.bankAreaWH().width, autoChopConfig.bankAreaWH().height, autoChopConfig.bankAreaPlane()-1)
        treeArea = WorldArea(autoChopConfig.treeAreaXY().width, autoChopConfig.treeAreaXY().height, autoChopConfig.treeAreaWH().width, autoChopConfig.treeAreaWH().height, autoChopConfig.treeAreaPlane()-1)
        bankDestination = WorldPoint(autoChopConfig.bankLocation().width, autoChopConfig.bankLocation().height, 0)
        treeDestination = WorldPoint(autoChopConfig.treeLocation().width, autoChopConfig.treeLocation().height, 0)


        when (state) { // State machine
            State.IDLE -> handleIdleState()
            State.SEARCHING -> handleSearchingState()
            State.CUTTING -> handleCuttingState()
            State.WALKING_TO_BANK -> handleWalkingToBankState()
            State.BANKING -> handleBankingState()
            State.WALKING_TO_TREES -> handleWalkingToTreesState()
            State.BURN_LOGS -> handleBurnLogsState()
            State.TREE_ROOT -> handleTreeRootState()
            State.FOX_TRAP -> handleFoxTrapState()
            State.RAINBOW -> handleRainbowState()
            State.BEE_HIVE -> handleBeeHiveState()
        }
    }

    private fun handleBeeHiveState() {
        if (!EthanApiPlugin.isMoving() && client.localPlayer.animation == -1) {
            if (beeHiveExists() && !Inventory.search().nameContains("ogs").empty()) {
                TileObjects.search().nameContains("nfinished Beehive").withAction("Finish").nearestToPlayer()
                    .ifPresent { beeHive ->
                        TileObjectInteraction.interact(beeHive, "Finish")
                    }
                tickDelay = 1
                return
            } else {
                changeStateTo(State.IDLE, 1)
            }
        }
    }

    private fun handleRainbowState() {
        if (!EthanApiPlugin.isMoving() && client.localPlayer.animation == -1) {
            if (rainbowExists()) {
                if (client.localPlayer.worldLocation != rainbowLocation()) {
                    PathingTesting.walkTo(rainbowLocation())
                    tickDelay = 1
                    return
                }
            } else {
                changeStateTo(State.IDLE, 1)
            }
        }
    }

    private fun handleFoxTrapState() {
        if (!EthanApiPlugin.isMoving() && client.localPlayer.animation == -1){
            if (foxTrapExists()){
                NPCs.search().nameContains("ox trap").withAction("Disarm").nearestToPlayer().ifPresent { foxTrap ->
                    NPCInteraction.interact(foxTrap, "Disarm")
                }
                tickDelay = 1
                return
            } else {
                changeStateTo(State.IDLE, 3)
            }
        }
    }

    private fun handleTreeRootState() {
        if (!EthanApiPlugin.isMoving() && client.localPlayer.animation == -1){
            if (treeRootExists()){
                TileObjects.search().nameContains("infused Tree root").withAction("Chop down").nearestToPlayer().ifPresent { treeRoot ->
                    TileObjectInteraction.interact(treeRoot, "Chop down")
                }
                tickDelay = 1
                return
            } else {
                changeStateTo(State.IDLE, 1)
            }
        }
    }

    private fun handleBurnLogsState() {
        if (!EthanApiPlugin.isMoving() && client.localPlayer.animation == -1){
            if (Widgets.search().withTextContains("What would you like to burn").result().isNotEmpty()){
                keyboard.keyPress(KeyEvent.VK_SPACE)
                tickDelay = 1
                return
            }
            if (Inventory.search().nameContains(autoChopConfig.logName()).result().isNotEmpty()){
                TileObjects.search().nameContains("Campfire").withAction("Tend-to").nearestToPlayer().ifPresent { campire ->
                    TileObjectInteraction.interact(campire, "Tend-to")
                }
                tickDelay = 1
                return
            }
            if (Inventory.search().nameContains(autoChopConfig.logName()).result().isEmpty()){
                changeStateTo(State.IDLE, 1)
            }
        }
    }

    private fun handleWalkingToTreesState() {
        if (treeArea.contains(client.localPlayer.worldLocation)){
            changeStateTo(State.IDLE, 1)
        } else {
            if (!EthanApiPlugin.isMoving() || !treeArea.contains(client.localPlayer.worldLocation)) {
                PathingTesting.walkTo(treeDestination)
            }
        }
    }

    private fun handleWalkingToBankState() {
        if (bankingArea.contains(client.localPlayer.worldLocation)){
            changeStateTo(State.BANKING, 1)
        } else {
            if (!EthanApiPlugin.isMoving() || EthanApiPlugin.playerPosition().distanceTo(bankDestination) > 3 && !bankingArea.contains(client.localPlayer.worldLocation)) {
                PathingTesting.walkTo(bankDestination)
            }
            if (bankingArea.contains(client.localPlayer.worldLocation) && !EthanApiPlugin.isMoving()){
                changeStateTo(State.BANKING, 1)
            }
        }
    }

    private fun handleBankingState() {
        if (!Bank.isOpen() && !EthanApiPlugin.isMoving() && Inventory.full()){
            NPCs.search().nameContains("Banker").withAction("Bank").nearestToPlayer().ifPresent { banker ->
                NPCInteraction.interact(banker, "Bank")
            }
            tickDelay = 1
            return
        }
        if (Bank.isOpen()){
            BankInventory.search().nameContains("ogs").withAction("Deposit-All").first().ifPresent { log ->
                BankInventoryInteraction.useItem(log, "Deposit-All")
            }
            changeStateTo(State.IDLE, 1)
        }
    }

    private fun handleCuttingState() {
        if (!EthanApiPlugin.isMoving() && client.localPlayer.animation == -1) {
            if (Inventory.full()) {
                if (autoChopConfig.burnLogs()) changeStateTo(State.BURN_LOGS, 1) else changeStateTo(State.WALKING_TO_BANK, 1)
            } else {
                changeStateTo(State.IDLE, 1)
            }
        }
    }

    private fun handleSearchingState() {
        TileObjects.search().nameContains(autoChopConfig.treeName()).withAction(autoChopConfig.treeAction()).nearestToPoint(getObjectWMostPlayers()).ifPresent { tree ->
            TileObjectInteraction.interact(tree, autoChopConfig.treeAction())
            changeStateTo(State.CUTTING, 1)
        }
    }

    private fun handleIdleState() {
        if(runIsOff() && client.energy >= 20 * 100){
            MousePackets.queueClickPacket()
            WidgetPackets.queueWidgetActionPacket(1, 10485787, -1, -1)
        }

        if (Inventory.full()) {
            if (autoChopConfig.burnLogs()) {
                changeStateTo(State.BURN_LOGS, 1)
            } else {
                if (bankingArea.contains(client.localPlayer.worldLocation)){
                    changeStateTo(State.BANKING, 1)
                } else {
                    changeStateTo(State.WALKING_TO_BANK, 1)
                }
            }
        } else {
            if (!treeArea.contains(client.localPlayer.worldLocation)) {
                changeStateTo(State.WALKING_TO_TREES, 1)
            } else {
                if(treeRootExists()) changeStateTo(State.TREE_ROOT, 1)
                else if(foxTrapExists()) changeStateTo(State.FOX_TRAP, 1)
                else if (rainbowExists()) changeStateTo(State.RAINBOW, 1)
                else if (beeHiveExists()) changeStateTo(State.BEE_HIVE, 1)
                else changeStateTo(State.SEARCHING)
            }
        }
    }

    private fun getObjectWMostPlayers(): WorldPoint {
        val objectName: String = autoChopConfig.treeName()
        val playerCounts: MutableMap<WorldPoint, Int> = HashMap()
        var mostPlayersTile: WorldPoint? = null
        var highestCount = 0
        val objects = TileObjects.search().withName(objectName).result()

        val players = Players.search().notLocalPlayer().result()

        for (tree in objects) {
            for (player in players) {
                if (player.worldLocation.distanceTo(tree.worldLocation) <= 2) {
                    val playerTile = player.worldLocation
                    playerCounts[playerTile] = playerCounts.getOrDefault(playerTile, 0) + 1
                    if (playerCounts[playerTile]!! > highestCount) {
                        highestCount = playerCounts[playerTile]!!
                        mostPlayersTile = playerTile
                    }
                }
            }
        }

        return mostPlayersTile ?: client.localPlayer.worldLocation
    }

    private fun foxTrapExists(): Boolean = NPCs.search().nameContains("ox trap").result().isNotEmpty()
    private fun treeRootExists(): Boolean = TileObjects.search().nameContains("ree root").result().isNotEmpty()
    private fun rainbowExists(): Boolean = TileObjects.search().nameContains("ainbow").result().isNotEmpty()
    private fun beeHiveExists(): Boolean = TileObjects.search().nameContains("nfinished Beehive").result().isNotEmpty()

    private fun rainbowLocation(): WorldPoint =
        TileObjects.search().nameContains("ainbow").result().first().worldLocation


    private fun changeStateTo(stateName: State, ticksToDelay: Int = 0) {
        state = stateName
        tickDelay = ticksToDelay
        // println("State : $stateName")
    }

    private fun runIsOff(): Boolean {
        return EthanApiPlugin.getClient().getVarpValue(173) == 0
    }
}