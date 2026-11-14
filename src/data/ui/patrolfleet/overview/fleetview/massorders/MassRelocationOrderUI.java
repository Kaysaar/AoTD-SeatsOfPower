package data.ui.patrolfleet.overview.fleetview.massorders;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.patrolfleet.managers.AoTDFactionPatrolsManager;
import data.scripts.patrolfleet.models.BasePatrolFleet;
import data.ui.patrolfleet.overview.OverviewPatrolPanel;
import data.ui.patrolfleet.overview.fleetview.FleetButtonComponent;
import data.ui.patrolfleet.overview.fleetview.fleetreloc.FleetRelocationSelectorPanel;
import data.ui.patrolfleet.overview.fleetview.templatelist.FleetButtonDrop;
import data.ui.patrolfleet.overview.fleetview.templatelist.FleetTable;

import data.scripts.patrolfleet.utilis.PatrolAssignmentService;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MassRelocationOrderUI implements ExtendedUIPanelPlugin {

    protected CustomPanelAPI mainPanel;
    protected CustomPanelAPI contentPanel;
    protected CustomPanelAPI infoPanel;
    protected CustomPanelAPI infoPanelContent;

    protected FleetTable tableOfFleets;
    protected FleetRelocationSelectorPanel relocationSelector;

    protected StarSystemAPI sourceSystem;
    protected ArrayList<BasePatrolFleet> fleetsToRelocate = new ArrayList<>();

    protected float widthFirstSection = 700f;
    protected float infoHeight = 60f;
    protected float headingHeight = 20f;
    protected float gap = 5f;

    public MassRelocationOrderUI(float width, float height, StarSystemAPI sourceSystem) {
        this.sourceSystem = sourceSystem;
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public ArrayList<BasePatrolFleet> getFleetsToRelocate() {
        return fleetsToRelocate;
    }

    public StarSystemAPI getSelectedSystem() {
        if (relocationSelector == null) return null;
        return relocationSelector.getSelectedSystem();
    }

    public boolean shouldEnableConfirm() {
        return !fleetsToRelocate.isEmpty()
                && relocationSelector != null
                && relocationSelector.hasSelection()
                && PatrolAssignmentService.canRelocate(sourceSystem, getSelectedSystem());
    }

    public void executeConfirmSection() {
        if (!shouldEnableConfirm()) return;
        if (AoTDFactionPatrolsManager.getInstance().relocateFleets(fleetsToRelocate, getSelectedSystem())) {
            OverviewPatrolPanel.forceRequestUpdate = true;
        }
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }

        float width = mainPanel.getPosition().getWidth();
        float height = mainPanel.getPosition().getHeight();

        contentPanel = Global.getSettings().createCustom(width, height, null);

        float widthSecondSection = width - widthFirstSection - gap;

        float infoY = height - infoHeight;
        float tableY = headingHeight + gap;
        float tableHeight = infoY - tableY - gap;

        TooltipMakerAPI tlFirst = contentPanel.createUIElement(widthFirstSection, headingHeight, false);
        TooltipMakerAPI tlSecond = contentPanel.createUIElement(widthSecondSection, headingHeight, false);

        tlFirst.addSectionHeading("Fleets to relocate", Alignment.MID, 0f);
        tlSecond.addSectionHeading("Relocation target", Alignment.MID, 0f);

        contentPanel.addUIElement(tlFirst).inTL(0f, 0f);
        contentPanel.addUIElement(tlSecond).inTL(widthFirstSection + gap, 0f);
        final MassRelocationOrderUI self = this;
        if (tableOfFleets == null) {
            tableOfFleets = new FleetTable(
                    widthFirstSection,
                    tableHeight,
                    true,
                    0,
                    0,
                    AoTDFactionPatrolsManager.getInstance().getAssignedFleetsForSystemForRelocation(sourceSystem)
            ) {
                @Override
                public void reportButtonPressed(CustomButton buttonPressed) {
                    if (buttonPressed instanceof FleetButtonComponent component) {
                        BasePatrolFleet fleet = component.getData();

                        if (fleetsToRelocate.contains(fleet)) {
                            fleetsToRelocate.remove(fleet);
                        } else {
                            fleetsToRelocate.add(fleet);
                        }

                        self.updateInfo();
                    }
                }

                @Override
                public void advance(float amount) {
                    super.advance(amount);

                    dropDownButtons.forEach(x -> {
                        if (x instanceof FleetButtonDrop bt) {
                            if (fleetsToRelocate.contains(bt.fleet)) {
                                x.mainButton.mainButton.highlight();
                            } else {
                                x.mainButton.mainButton.unhighlight();
                            }
                        }
                    });
                }
            };

            tableOfFleets.createSections();
            tableOfFleets.createTable();
        }

        if (relocationSelector == null) {
            relocationSelector = new FleetRelocationSelectorPanel(
                    widthSecondSection,
                    height - tableY,
                    sourceSystem
            ){
                @Override
                public void updateInfo() {
                    super.updateInfo();
                    self.updateInfo();
                }
            };
        }

        contentPanel.addComponent(tableOfFleets.mainPanel).inTL(0f, tableY);
        contentPanel.addComponent(relocationSelector.getMainPanel()).inTL(widthFirstSection + gap, tableY);

        // The destination selector owns the right-hand summary area.
        infoPanel = Global.getSettings().createCustom(widthFirstSection, infoHeight, null);
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

        StarSystemAPI target = getSelectedSystem();

        if (fleetsToRelocate.isEmpty()) {
            tooltip.addPara(
                    "Select at least one fleet to relocate.",
                    3f,
                    Color.ORANGE
            );
        } else if (target == null) {
            String fleets = fleetsToRelocate.size() == 1 ? "one fleet" : "multiple fleets";

            tooltip.addPara(
                    "You have selected %s for relocation.",
                    3f,
                    Color.ORANGE,
                    fleets
            );

            tooltip.addPara(
                    "Choose a destination system to proceed.",
                    8f,
                    Color.ORANGE
            );
        } else {
            String fleets = fleetsToRelocate.size() == 1 ? "one fleet" : "multiple fleets";

            tooltip.addPara(
                    "You are about to relocate %s from %s.",
                    3f,
                    Color.ORANGE,
                    fleets,
                    sourceSystem.getName()
            );

        }

        infoPanelContent.addUIElement(tooltip).inTL(0f, 0f);
        infoPanel.addComponent(infoPanelContent).inTL(0f, 0f);
    }

    @Override
    public void clearUI() {
        if (tableOfFleets != null) {
            tableOfFleets.clearUI();
        }

        if (relocationSelector != null) {
            relocationSelector.clearUI();
        }

        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
            contentPanel = null;
        }

        infoPanel = null;
        infoPanelContent = null;
    }

    @Override
    public void advance(float amount) {
        if (tableOfFleets != null) {
            tableOfFleets.advance(amount);
        }

        if (relocationSelector != null) {
            boolean hadSelection = relocationSelector.hasSelection();

            StarSystemAPI before = relocationSelector.getSelectedSystem();
            relocationSelector.advance(amount);
            StarSystemAPI after = relocationSelector.getSelectedSystem();

            if (before != after || hadSelection != relocationSelector.hasSelection()) {
                updateInfo();
            }
        }
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
