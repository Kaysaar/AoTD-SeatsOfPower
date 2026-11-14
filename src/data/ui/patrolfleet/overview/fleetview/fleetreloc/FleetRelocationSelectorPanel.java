package data.ui.patrolfleet.overview.fleetview.fleetreloc;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.plugins.AoTDSopMisc;
import data.ui.patrolfleet.templates.shiplist.components.SortingState;

import data.scripts.patrolfleet.utilis.PatrolAssignmentService;

import java.awt.*;
import java.util.List;

public class FleetRelocationSelectorPanel implements ExtendedUIPanelPlugin {

    protected CustomPanelAPI mainPanel;
    protected CustomPanelAPI contentPanel;
    protected CustomPanelAPI infoPanel;
    protected CustomPanelAPI infoPanelContent;

    protected ButtonAPI buttonName;
    protected ButtonAPI buttonFPUsed;
    protected ButtonAPI buttonDistance;

    protected FleetRelocMarketList list;
    protected FleetLocationData currChosen;
    protected final FactionAPI faction;
    protected final StarSystemAPI sourceSystem;

    protected final float width;
    protected final float height;

    public FleetRelocationSelectorPanel(float width, float height, StarSystemAPI sourceSystem) {
        this.width = width;
        this.height = height;
        this.faction = Global.getSector().getPlayerFaction();
        this.sourceSystem = sourceSystem;

        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public FleetLocationData getCurrChosen() {
        return currChosen;
    }

    public StarSystemAPI getSelectedSystem() {
        if (currChosen == null) return null;
        return (StarSystemAPI) currChosen.buttonData;
    }

    public boolean hasSelection() {
        return PatrolAssignmentService.canRelocate(sourceSystem, getSelectedSystem());
    }

    public float getTravelDaysToSelectedSystem() {
        StarSystemAPI system = getSelectedSystem();
        if (system == null || sourceSystem == null) return 0f;

        return RouteLocationCalculator.getTravelDays(
                sourceSystem.getCenter(),
                system.getCenter()
        );
    }

    public boolean isSelectedSystemConnectedByBifrost() {
        return PatrolAssignmentService.connectedByBifrost(sourceSystem, getSelectedSystem());
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }

        contentPanel = Global.getSettings().createCustom(width, height, null);

        float usableWidth = width - 13f;
        float section = usableWidth / 3f;

        Color base = Misc.getBasePlayerColor();
        Color bg = Misc.getDarkPlayerColor();
        Color bright = Misc.getBrightPlayerColor();

        float headerHeight = 20f;
        float infoHeight = 60f;
        float gap = 2f;

        CustomPanelAPI headerPanel = Global.getSettings().createCustom(width, headerHeight, null);
        TooltipMakerAPI headerTooltip = headerPanel.createUIElement(width, headerHeight, false);

        buttonName = headerTooltip.addAreaCheckbox(
                "Name",
                SortingState.ASCENDING,
                base,
                bg,
                bright,
                section,
                20f,
                0f
        );

        buttonFPUsed = headerTooltip.addAreaCheckbox(
                "FP allocated",
                SortingState.NON_INITIALIZED,
                base,
                bg,
                bright,
                section,
                20f,
                0f
        );

        buttonDistance = headerTooltip.addAreaCheckbox(
                "Distance (LY)",
                SortingState.NON_INITIALIZED,
                base,
                bg,
                bright,
                section,
                20f,
                0f
        );

        buttonName.getPosition().inTL(2f, 0f);
        buttonFPUsed.getPosition().inTL(section + 2f, 0f);
        buttonDistance.getPosition().inTL((section + 1f) * 2f+3f, 0f);

        buttonName.setClickable(false);
        buttonFPUsed.setClickable(false);
        buttonDistance.setClickable(false);

        headerPanel.addUIElement(headerTooltip).inTL(0f, 0f);
        contentPanel.addComponent(headerPanel).inTL(0f, 0f);

        float infoY = height - infoHeight;

        float tableY = headerHeight + gap;
        float tableHeight = infoY - tableY - gap;

        list = new FleetRelocMarketList(
                width,
                tableHeight,
                faction,
                sourceSystem
        );

        list.createUI();
        contentPanel.addComponent(list.getMainPanel()).inTL(3f, tableY);

        infoPanel = Global.getSettings().createCustom(width - 10f, infoHeight, null);
        contentPanel.addComponent(infoPanel).inTL(0f, infoY);

        mainPanel.addComponent(contentPanel).inTL(0f, 0f);

        updateInfo();
    }
    public void updateInfo() {
        if (infoPanel == null) return;

        if (infoPanelContent != null) {
            infoPanel.removeComponent(infoPanelContent);
        }

        infoPanelContent = Global.getSettings().createCustom(
                infoPanel.getPosition().getWidth(),
                infoPanel.getPosition().getHeight(),
                null
        );

        TooltipMakerAPI tooltip = infoPanelContent.createUIElement(
                infoPanelContent.getPosition().getWidth() - 10f,
                infoPanelContent.getPosition().getHeight(),
                true
        );

        tooltip.setParaFont(Fonts.ORBITRON_20AABOLD);

        if (!FleetRelocMarketList.doesPlayerFactionMeetCriteriaForInterstellarReloc()) {
            tooltip.addPara(
                    "Interstellar relocation requires your faction to control %s.",
                    3f,
                    Color.ORANGE,
                    AoTDSopMisc.getAllIndustriesJoined(
                            FleetRelocMarketList.industriesAllowingInterstellarTransition.stream().toList(),
                            "or"
                    )
            ).setAlignment(Alignment.MID);
        }

        StarSystemAPI system = getSelectedSystem();

        if (system != null) {
            int baseCount = PatrolAssignmentService.getViableBases(system).size();
            tooltip.addPara("Distribute evenly across %s operational "
                    + (baseCount == 1 ? "base." : "bases."), 3f, Color.ORANGE, "" + baseCount);
            if (isSelectedSystemConnectedByBifrost()) {
                tooltip.addPara(
                        "Travel via %s: a few days.",
                        8f,
                        Color.ORANGE,
                        "Bifrost Network"
                );
            } else {
                tooltip.addPara(
                        "Estimated travel time: %s.",
                        8f,
                        Color.ORANGE,
                        AshMisc.convertDaysToString(Math.round(getTravelDaysToSelectedSystem()))
                );
            }
        }

        infoPanelContent.addUIElement(tooltip).inTL(0f, 0f);
        infoPanel.addComponent(infoPanelContent).inTL(0f, 0f);
    }

    @Override
    public void advance(float amount) {
        if (list == null || list.data == null) return;

        for (FleetLocationData datum : list.data) {
            if (datum.mainButton.isChecked()) {
                datum.mainButton.setChecked(false);
                currChosen = datum;
                updateInfo();
                break;
            }
        }

        for (FleetLocationData datum : list.data) {
            if (datum.equals(currChosen)) {
                datum.mainButton.highlight();
            } else {
                datum.mainButton.unhighlight();
            }
        }
    }

    @Override
    public void clearUI() {
        if (list != null) {
            list.clearUI();
        }

        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
            contentPanel = null;
        }

        infoPanel = null;
        infoPanelContent = null;
        buttonName = null;
        buttonFPUsed = null;
        buttonDistance = null;
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
    public void processInput(List<InputEventAPI> events) {

    }

    @Override
    public void buttonPressed(Object buttonId) {

    }
}
