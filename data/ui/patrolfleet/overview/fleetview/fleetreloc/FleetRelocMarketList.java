package data.ui.patrolfleet.overview.fleetview.fleetreloc;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.patrolfleet.utilis.PatrolAssignmentService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public class FleetRelocMarketList implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel,componentPanel;
    FactionAPI faction;
    ArrayList<FleetLocationData>data = new ArrayList<>();
    StarSystemAPI systemOrigin;
    public static LinkedHashSet<String> industriesAllowingInterstellarTransition = new LinkedHashSet<>();
    static {
        industriesAllowingInterstellarTransition.add(Industries.HIGHCOMMAND);
        industriesAllowingInterstellarTransition.add("aotd_hexagon");
    }
    public static boolean doesPlayerFactionMeetCriteriaForInterstellarReloc() {
        return PatrolAssignmentService.isInterstellarRelocationUnlocked();
    }
    public FleetRelocMarketList(float width, float height, FactionAPI faction,StarSystemAPI systemOrigin){
        mainPanel = Global.getSettings().createCustom(width,height,this);
        this.faction = faction;
        this.systemOrigin = systemOrigin;
    }
    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (componentPanel != null) mainPanel.removeComponent(componentPanel);
        data.clear();
        componentPanel = Global.getSettings().createCustom(mainPanel.getPosition().getWidth(), mainPanel.getPosition().getHeight(), null);
        TooltipMakerAPI tooltip = componentPanel.createUIElement(componentPanel.getPosition().getWidth(), componentPanel.getPosition().getHeight(), true);
        List<StarSystemAPI> systems = new ArrayList<>(PatrolAssignmentService.getManagedSystems());
        systems.removeIf(system -> !PatrolAssignmentService.canRelocate(systemOrigin, system));
        systems.sort(Comparator.comparingDouble(system -> Misc.getDistanceLY(systemOrigin.getCenter(), system.getCenter())));
        for (StarSystemAPI system : systems) {
            FleetLocationData button = new FleetLocationData(componentPanel.getPosition().getWidth()-10, 50,
                    system, 0f, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(),
                    false, Misc.getDistanceLY(systemOrigin.getCenter(), system.getCenter()));
            button.createUI();
            data.add(button);
            tooltip.addCustom(button.getPanel(), 5f);
        }
        componentPanel.addUIElement(tooltip).inTL(-10,0);
        mainPanel.addComponent(componentPanel);
    }

    @Override
    public void clearUI() {
        data.clear();
    }

    @Override
    public void positionChanged(PositionAPI position) {

    }

    @Override
    public void renderBelow(float alphaMult) {

    }

    @Override
    public void render(float alphaMult) {

    }

    @Override
    public void advance(float amount) {

    }

    @Override
    public void processInput(List<InputEventAPI> events) {

    }

    @Override
    public void buttonPressed(Object buttonId) {

    }
}
