package data.ui.patrolfleet.overview;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import data.scripts.patrolfleet.managers.AoTDFactionPatrolsManager;
import data.ui.patrolfleet.overview.marketdata.FleetMarketData;
import data.ui.patrolfleet.overview.stats.OverviewStatPanel;

import java.util.List;

public class OverviewPatrolPanel  implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, componentPanel;
    OverviewStatPanel stats;
    FleetMarketData data;
    StarSystemAPI lastSavedSystem = null;
    public static float OverviewStatPanelWidth = 460;
    public static boolean forceRequestUpdate = false;
    public static boolean forceRequestUpdateListOnly = false;
    public static boolean forceRequestUpdateStatsOnly = false;

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public OverviewPatrolPanel(float width, float height) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();

    }

    @Override
    public void createUI() {
        if(componentPanel!=null){
            mainPanel.removeComponent(componentPanel);
        }
        AoTDFactionPatrolsManager.getInstance().advanceAfterFleets(0f);
        componentPanel = Global.getSettings().createCustom(mainPanel.getPosition().getWidth(), mainPanel.getPosition().getHeight(), null);
        if(stats!=null){
            stats.clearUI();
        }
        stats = new OverviewStatPanel(OverviewStatPanelWidth,mainPanel.getPosition().getHeight());
        if(data!=null){
            data.clearUI();
        }
        data = new FleetMarketData(mainPanel.getPosition().getWidth()-OverviewStatPanelWidth-5,mainPanel.getPosition().getHeight());
        data.setSystem(stats.getCurrentSystem());
        componentPanel.addComponent(stats.getMainPanel()).inTL(0,0);
        componentPanel.addComponent(data.getMainPanel()).inTL(OverviewStatPanelWidth+5,0);

        // createUI

        mainPanel.addComponent(componentPanel).inTL(0,0);

    }

    @Override
    public void clearUI() {

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
        if(forceRequestUpdateListOnly){
            forceRequestUpdateListOnly = false;
            lastSavedSystem = stats.getCurrentSystem();
            data.setSystem(lastSavedSystem);
        }
        if(forceRequestUpdate){
            forceRequestUpdate = false;
            AoTDFactionPatrolsManager.getInstance().advanceAfterFleets(0f);
            data.createUI();
            stats.createUI();


        }
        if(forceRequestUpdateStatsOnly){
            forceRequestUpdateStatsOnly = false;
            AoTDFactionPatrolsManager.getInstance().advanceAfterFleets(0f);
            stats.createUI();
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {

    }

    @Override
    public void buttonPressed(Object buttonId) {

    }
}
