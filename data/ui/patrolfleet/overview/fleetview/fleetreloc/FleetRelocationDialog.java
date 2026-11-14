package data.ui.patrolfleet.overview.fleetview.fleetreloc;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.ui.*;

import data.scripts.patrolfleet.models.BasePatrolFleet;
import data.scripts.patrolfleet.managers.AoTDFactionPatrolsManager;
import data.ui.patrolfleet.overview.OverviewPatrolPanel;


import java.awt.*;
import java.util.Collections;

public class FleetRelocationDialog extends BasePopUpDialog {
    // AshLib places its 25px buttons 40px from the bottom; leave 15px above them.
    private static final float FOOTER_HEIGHT = 55f;
    private float contentHeight;
    BasePatrolFleet fleetData;
    FleetRelocationSelectorPanel relocationPanel;

    public FleetRelocationDialog(BasePatrolFleet fleetData) {
        super("Relocate Fleet");
        this.fleetData = fleetData;

    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        createHeaader(panelAPI);
        float width = panelAPI.getPosition().getWidth() - 30f;
        contentHeight = panelAPI.getPosition().getHeight() - y - FOOTER_HEIGHT;
        TooltipMakerAPI tooltip = panelAPI.createUIElement(width, contentHeight, false);
        createContentForDialog(tooltip, width);
        addTooltip(tooltip);
        panelAPI.addUIElement(tooltip).inTL(x, y);
        createConfirmAndCancelSection(panelAPI);
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        tooltip.setParaFont(Fonts.ORBITRON_20AABOLD);
        tooltip.addPara("Choose the destination star system", 2f)
                .setAlignment(Alignment.MID);

        float panelGap = 5f;
        float panelHeight = contentHeight - tooltip.getHeightSoFar() - panelGap;

        relocationPanel = new FleetRelocationSelectorPanel(
                width,
                panelHeight,
                fleetData.getAssignedSystem()
        );

        tooltip.addCustom(relocationPanel.getMainPanel(), panelGap);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (relocationPanel != null) {
            relocationPanel.advance(amount);
            if (getConfirmButton() != null) getConfirmButton().setEnabled(relocationPanel.hasSelection());
        }
    }

    @Override
    public void applyConfirmScript() {
        if (relocationPanel == null || !relocationPanel.hasSelection()) return;
        if (AoTDFactionPatrolsManager.getInstance().relocateFleets(
              Collections.singletonList(fleetData), relocationPanel.getSelectedSystem())) {
            OverviewPatrolPanel.forceRequestUpdate = true;
        }
    }

    @Override
    public void onExit() {
        if (relocationPanel != null) {
            relocationPanel.clearUI();
        }
    }
}
