package data.ui.patrolfleet.overview.fleetview.dialogs;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.ui.patrolfleet.overview.fleetview.FleetOptions;
import data.ui.patrolfleet.overview.fleetview.massorders.MassRelocationOrderUI;

public class MassRelocationDialog extends BasePopUpDialog {

    // Keep content above AshLib's bottom button row, with a 15px gap.
    private static final float FOOTER_HEIGHT = 55f;
    MassRelocationOrderUI order;
    float height = 0f;
    StarSystemAPI system;
    FleetOptions options;

    public MassRelocationDialog(StarSystemAPI systemToRelocateFrom, FleetOptions options) {
        super("Order Mass Relocation from " + systemToRelocateFrom.getName());
        this.system = systemToRelocateFrom;
        this.options = options;
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        this.createHeaader(panelAPI);

        height = panelAPI.getPosition().getHeight() - this.y - FOOTER_HEIGHT;
        TooltipMakerAPI tooltip = panelAPI.createUIElement(
                panelAPI.getPosition().getWidth() - 30f,
                height,
                false
        );

        this.createContentForDialog(
                tooltip,
                panelAPI.getPosition().getWidth() - 30f
        );

        this.addTooltip(tooltip);
        panelAPI.addUIElement(tooltip).inTL(this.x, this.y);
        this.createConfirmAndCancelSection(panelAPI);
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        order = new MassRelocationOrderUI(width, height, system);
        tooltip.addCustom(order.getMainPanel(), 0f);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (order != null) {
            order.advance(amount);
        }

        if (getConfirmButton() != null && order != null) {
            boolean shouldEnable = order.shouldEnableConfirm();

            if (getConfirmButton().isEnabled() != shouldEnable) {
                getConfirmButton().setEnabled(shouldEnable);
            }
        }
    }

    @Override
    public void applyConfirmScript() {
        super.applyConfirmScript();

        if (order != null) {
            order.executeConfirmSection();
            order.clearUI();
        }

        options.forceDismiss();
    }

    @Override
    public void onExit() {
        if (order != null) {
            order.clearUI();
        }
    }
}
