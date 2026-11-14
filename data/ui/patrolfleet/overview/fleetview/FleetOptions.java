package data.ui.patrolfleet.overview.fleetview;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.PopUpUI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.patrolfleet.models.BasePatrolFleet;
import data.ui.patrolfleet.overview.fleetview.dialogs.MassDecommissionDialog;
import data.ui.patrolfleet.overview.fleetview.dialogs.MassDeploymentDialog;
import data.ui.patrolfleet.overview.fleetview.dialogs.MassRelocationDialog;
import data.ui.patrolfleet.overview.marketdata.FleetMarketData;

import data.scripts.patrolfleet.utilis.PatrolAssignmentService;

import java.awt.*;

public class FleetOptions extends PopUpUI {
    CustomPanelAPI mainPanel;
    ButtonAPI change,delete,relocate;
    FleetMarketData data;

    public FleetMarketData getData() {
        return data;
    }

    boolean massOrderMode = false;
    public FleetOptions(FleetMarketData fleet) {
        this.data = fleet;
    }
    public FleetOptions(FleetMarketData fleet,boolean massOrderMode) {
        this.data = fleet;
        this.massOrderMode = massOrderMode;
    }
    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        createUIMockup(panelAPI);
        panelAPI.addComponent(mainPanel).inTL(0, 0);
    }

    @Override
    public float createUIMockup(CustomPanelAPI panelAPI) {
        mainPanel = panelAPI.createCustomPanel(panelAPI.getPosition().getWidth(), panelAPI.getPosition().getHeight(), null);
        TooltipMakerAPI tooltip = mainPanel.createUIElement(panelAPI.getPosition().getWidth(),panelAPI.getPosition().getHeight(),true);
        FactionAPI pirates  = Global.getSector().getFaction(Factions.PIRATES);
        tooltip.addSpacer(0f).getPosition().inTL(7,0);
        if(!massOrderMode){
            change = tooltip.addButton("Edit fleet",null, Misc.getBasePlayerColor(),Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.NONE,mainPanel.getPosition().getWidth()-5,30,3f);
            relocate = tooltip.addButton("Relocate fleet",null, Misc.getBasePlayerColor(),Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.NONE,mainPanel.getPosition().getWidth()-5,30,3f);
            delete = tooltip.addButton("Delete fleet",null, pirates.getBaseUIColor(),pirates.getDarkUIColor(), Alignment.MID, CutStyle.NONE,mainPanel.getPosition().getWidth()-5,30,3f);
            BasePatrolFleet fleet = data.lastChecked.getData();
            if(fleet.isDecomisioned()||fleet.isInTransit()){
                change.setEnabled(false);
                relocate.setEnabled(false);
                delete.setEnabled(false);
            }
            if(fleet.isGrounded()){
                relocate.setEnabled(false);
            }
        }
        else{
            change = tooltip.addButton("Order Massive Deployment",null, Misc.getBasePlayerColor(),Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.NONE,mainPanel.getPosition().getWidth()-5,30,3f);
            relocate = tooltip.addButton("Order Rapid Re-location",null, Misc.getBasePlayerColor(),Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.NONE,mainPanel.getPosition().getWidth()-5,30,3f);
            delete = tooltip.addButton("Order Massive De-Commission",null, pirates.getBaseUIColor(),pirates.getDarkUIColor(), Alignment.MID, CutStyle.NONE,mainPanel.getPosition().getWidth()-5,30,3f);
        }

        boolean hasDestination = PatrolAssignmentService.getManagedSystems().stream()
                .anyMatch(system -> PatrolAssignmentService.canRelocate(data.getSystem(), system));
        if (!hasDestination) {
            relocate.setEnabled(false);
            tooltip.addTooltipTo(new TooltipMakerAPI.TooltipCreator() {
                public boolean isTooltipExpandable(Object param) { return false; }
                public float getTooltipWidth(Object param) { return 400; }
                public void createTooltip(TooltipMakerAPI tip, boolean expanded, Object param) {
                    tip.addPara("Relocation requires a High Command or Hexagon under faction control, and another system with an operational patrol base.", 3f);
                }
            }, relocate, TooltipMakerAPI.TooltipLocation.BELOW, false);
        }
        if (massOrderMode) {
            change.setEnabled(!PatrolAssignmentService.getViableBases(data.getSystem()).isEmpty());
            if (data.getSystem() == null) { relocate.setEnabled(false); delete.setEnabled(false); }
        }
        tooltip.addSpacer(3f);
        mainPanel.getPosition().setSize(panelAPI.getPosition().getWidth(),tooltip.getHeightSoFar());
        mainPanel.addUIElement(tooltip).inTL(-3,0);
        return tooltip.getHeightSoFar();


    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if(massOrderMode){
            if(isButtonChecked(change)){
                change.setChecked(false);
                MassDeploymentDialog dialog = new MassDeploymentDialog(data.getSystem(),this);
                AshMisc.initPopUpDialog(dialog,1250,640);
            }
            if(isButtonChecked(delete)){
                delete.setChecked(false);
                MassDecommissionDialog dialog = new MassDecommissionDialog(data.getSystem(),this);
                AshMisc.initPopUpDialog(dialog,1250,640);
            }
            if(isButtonChecked(relocate)){
                relocate.setChecked(false);
                MassRelocationDialog dialog = new MassRelocationDialog(data.getSystem(),this);
                AshMisc.initPopUpDialog(dialog,1250,640);
            }
        }
        else {
            if(isButtonChecked(change)){
                data.showEdit = true;
                forceDismiss();
            }
            if(isButtonChecked(relocate)){
                data.showReloc = true;
                forceDismiss();
            }
            if(isButtonChecked(delete)){
                data.showDelete = true;
                forceDismiss();
            }
        }


    }
    public boolean isButtonChecked(ButtonAPI button){
        return button!=null&& button.isChecked();
    }

    @Override
    public void onExit() {
        super.onExit();
    }
}
