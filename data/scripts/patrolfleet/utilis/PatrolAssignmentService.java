package data.scripts.patrolfleet.utilis;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.aotd_entities.BiFrostGateEntity;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import data.industry.AoTDMilitaryBase;
import data.kaysaar.aotd.vok.campaign.econ.megastructures.impl.scripts.BifrostMegastructure;
import data.kaysaar.aotd.vok.campaign.econ.megastructures.impl.scripts.BifrostMegastructureManager;
import data.scripts.patrolfleet.managers.AoTDFactionPatrolsManager;
import data.scripts.patrolfleet.models.BasePatrolFleet;
import data.ui.patrolfleet.overview.fleetview.fleetreloc.FleetRelocEventListener;

import java.util.*;

/** Shared command, base eligibility, route retirement, and physical transfer rules. */
public final class PatrolAssignmentService {
    public static final String FLEET_ID_KEY = "$aotd_system_patrol_id";
    public static final String ROUTE_KEY = "$aotd_system_patrol_route";
    public static final String RETURN_TARGET_KEY = "$aotd_system_patrol_return_target";
    private PatrolAssignmentService() {}

    public static boolean sameMarket(MarketAPI a, MarketAPI b) {
        return a == b || (a != null && b != null && a.getId().equals(b.getId()));
    }

    public static boolean sameSystem(StarSystemAPI a, StarSystemAPI b) {
        return a == b || (a != null && b != null && a.getId().equals(b.getId()));
    }

    public static boolean isViableBase(MarketAPI market, StarSystemAPI system) {
        if (market == null || system == null || !sameSystem(market.getStarSystem(), system)
                || market.getPrimaryEntity() == null || !market.isInEconomy() || market.isHidden()
                || market.getFaction() == null || !market.getFaction().isPlayerFaction()) return false;
        for (Industry industry : market.getIndustries()) {
            if (AoTDMilitaryBase.industriesValidForBase.contains(industry.getId()) && industry.isFunctional()) return true;
        }
        return false;
    }

    public static List<MarketAPI> getViableBases(StarSystemAPI system) {
        List<MarketAPI> result = new ArrayList<>();
        if (system == null) return result;
        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(system)) {
            if (isViableBase(market, system)) result.add(market);
        }
        result.sort(Comparator.comparing(MarketAPI::getId));
        return result;
    }

    public static List<StarSystemAPI> getManagedSystems() {
        Map<String, StarSystemAPI> systems = new LinkedHashMap<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            StarSystemAPI system = market.getStarSystem();
            if (system != null && market.getFaction() != null && market.getFaction().isPlayerFaction()
                    && !market.isHidden()) systems.put(system.getId(), system);
        }
        // Keep fleets accessible even if the last colony/base in their system is lost.
        for (BasePatrolFleet fleet : AoTDFactionPatrolsManager.getInstance().fleetsCurrentlyInField.values()) {
            if (fleet.getAssignedSystem() != null && !fleet.shouldRemove()) {
                systems.put(fleet.getAssignedSystem().getId(), fleet.getAssignedSystem());
            }
        }
        List<StarSystemAPI> result = new ArrayList<>(systems.values());
        result.sort(Comparator.comparing(StarSystemAPI::getName).thenComparing(StarSystemAPI::getId));
        return result;
    }

    public static boolean isInterstellarRelocationUnlocked() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getFaction() == null || !market.getFaction().isPlayerFaction()) continue;
            for (String id : Arrays.asList(Industries.HIGHCOMMAND, "aotd_hexagon")) {
                Industry industry = market.getIndustry(id);
                if (industry != null) return true; // Preserve the existing faction-wide unlock rule.
            }
        }
        return false;
    }

    public static boolean canRelocate(StarSystemAPI source, StarSystemAPI destination) {
        return source != null && destination != null && !sameSystem(source, destination)
                && isInterstellarRelocationUnlocked() && !getViableBases(destination).isEmpty();
    }

    public static boolean hasPatrol(BasePatrolFleet fleet) {
        RouteManager.RouteData route = fleet.getPatrolRoute();
        if (route == null) return false;
        CampaignFleetAPI active = route.getActiveFleet();
        return (active != null && active.isAlive())
                || RouteManager.getInstance().getRoutesForSource(route.getSource()).contains(route);
    }

    public static boolean needsReturn(BasePatrolFleet fleet) {
        return fleet.shouldRemove() || fleet.isDecomisioned() || fleet.isGrounded()
                || fleet.isInTransit() || !sameMarket(fleet.getTiedTo(), fleet.getAssignedBase())
                || !isViableBase(fleet.getTiedTo(), fleet.getAssignedSystem());
    }

    public static boolean canSpawnRoute(BasePatrolFleet fleet, RouteManager.RouteData route) {
        return fleet != null && fleet.getPatrolRoute() == route && !needsReturn(fleet)
                && !fleet.isTransferStarted() && sameMarket(route.getMarket(), fleet.getTiedTo());
    }

    public static void registerRoute(BasePatrolFleet fleet, RouteManager.RouteData route) {
        fleet.setPatrolRoute(route);
    }

    public static BasePatrolFleet getFleetForEntity(CampaignFleetAPI fleet) {
        String id = fleet.getMemoryWithoutUpdate().getString(FLEET_ID_KEY);
        return id == null ? null : AoTDFactionPatrolsManager.getInstance().getFleet(id);
    }

    public static void removePatrol(BasePatrolFleet fleet) {
        RouteManager.RouteData route = fleet.getPatrolRoute();
        fleet.setPatrolRoute(null);
        if (route != null) RouteManager.getInstance().removeRoute(route);
    }

    public static void reportPatrolDespawn(BasePatrolFleet data, CampaignFleetAPI fleet,
                                           CampaignEventListener.FleetDespawnReason reason) {
        RouteManager.RouteData route = data.getPatrolRoute();
        if (route == null || fleet.getMemoryWithoutUpdate().get(ROUTE_KEY) != route) return;
        CampaignFleetAPI active = route.getActiveFleet();
        if (active != null && active != fleet) return;
        // A normal offscreen despawn leaves the abstract patrol in RouteManager.
        if (needsReturn(data) || reason == CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION) {
            if (reason == CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION && needsReturn(data)) {
                Object remembered = fleet.getMemoryWithoutUpdate().get(RETURN_TARGET_KEY);
                if (remembered instanceof SectorEntityToken returnTarget) {
                    data.setTiedTo(returnTarget.getMarket());
                    data.setTransitOriginSystem(returnTarget.getStarSystem());
                }
            }
            removePatrol(data);
        }
        data.performReplacement();
    }

    public static SectorEntityToken getReturnTarget(BasePatrolFleet data, RouteManager.RouteData route) {
        MarketAPI origin = route.getMarket();
        if (origin != null && origin.getPrimaryEntity() != null && origin.isInEconomy()
                && origin.getFaction() != null && origin.getFaction().isPlayerFaction()) {
            return origin.getPrimaryEntity();
        }
        if (data.getAssignedBase() != null) return data.getAssignedBase().getPrimaryEntity();
        if (data.getAssignedSystem() != null) return data.getAssignedSystem().getCenter();
        return route.getCurrent() == null ? null : route.getCurrent().from;
    }

    public static void advanceFleet(BasePatrolFleet data, float amount) {
        if (!hasPatrol(data)) data.setPatrolRoute(null);
        if (needsReturn(data) && hasPatrol(data)) {
            CampaignFleetAPI active = data.getPatrolRoute().getActiveFleet();
            if (active != null && active.isAlive()) return; // AI returns the physical fleet first.
            removePatrol(data); // Offscreen routes can be retired immediately.
        }
        if (hasPatrol(data)) return;
        if (data.isTransferStarted()) {
            data.advanceTransitClock(amount);
            CampaignFleetAPI active = data.getRelocationFleet();
            if (active != null && active.isAlive()) {
                if (data.getTransferDestination() != null
                        && !isViableBase(data.getTransferDestination(), data.getAssignedSystem())) {
                    // Ownership/industry changed in flight. Reroute the existing entity, never spawn a second one.
                    configureTransfer(data, active, data.getAssignedBase(), active);
                }
                return;
            }
            data.setRelocationFleet(null);
            if (data.getDaysTillSomething() <= 0f) finishTransfer(data);
            return;
        }
        if (data.isDecomisioned() || data.isGrounded()) {
            data.setInTransit(false);
            return;
        }
        MarketAPI target = data.getAssignedBase();
        if (target == null || !isViableBase(target, data.getAssignedSystem())) {
            data.setInTransit(false);
            return;
        }
        if (sameMarket(data.getTiedTo(), target)) {
            data.setInTransit(false);
            data.setTransitOriginSystem(null);
            return;
        }
        startTransfer(data, target);
    }

    private static SectorEntityToken transferOrigin(BasePatrolFleet data) {
        if (data.getTiedTo() != null && data.getTiedTo().getPrimaryEntity() != null
                && data.getTiedTo().getPrimaryEntity().getContainingLocation() != null) return data.getTiedTo().getPrimaryEntity();
        StarSystemAPI source = data.getTransitOriginSystem();
        if (source == null) source = data.getAssignedSystem();
        return source == null ? null : source.getCenter();
    }

    private static void startTransfer(BasePatrolFleet data, MarketAPI target) {
        SectorEntityToken origin = transferOrigin(data);
        if (origin == null || origin.getContainingLocation() == null) return;
        MarketAPI source = data.getTiedTo();
        if (source == null || !source.isInEconomy() || source.getPrimaryEntity() == null) source = target;
        String faction = Global.getSector().getPlayerFaction().getId();
        FleetParamsV3 params = new FleetParamsV3(source, null, faction,
                Misc.getShipQuality(source, faction), null, data.getFPTaken(), 0f, 0f, 0f, 0f, 0f, 0f);
        CampaignFleetAPI active = PatrolFleetFactory.createFleetFromAssigned(data, params, source, params.random);
        if (active == null || active.isEmpty()) return;
        data.setRelocationFleet(active);
        data.setTransferStarted(true);
        origin.getContainingLocation().addEntity(active);
        active.setLocation(origin.getLocation().x, origin.getLocation().y);
        active.setName(data.getNameOfFleet());
        active.setNoFactionInName(data.isDontUseFactionPrefix());
        active.addEventListener(new FleetRelocEventListener(data));
        active.setTransponderOn(true);
        active.addAbility(Abilities.SUSTAINED_BURN);
        active.getAbility(Abilities.SUSTAINED_BURN).activate();
        active.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        configureTransfer(data, active, target, origin);
    }

    private static void configureTransfer(BasePatrolFleet data, CampaignFleetAPI active,
                                           MarketAPI destination, SectorEntityToken origin) {
        SectorEntityToken target = destination == null ? data.getAssignedSystem().getCenter() : destination.getPrimaryEntity();
        data.setTransferDestination(destination);
        float travelDays = Math.max(1f, RouteLocationCalculator.getTravelDays(origin, target));
        boolean bifrost = connectedByBifrost(origin.getStarSystem(), target.getStarSystem());
        data.setInTransit(true, bifrost ? 5f : travelDays + 1f);
        active.clearAssignments();
        if (origin != active) active.addAssignment(FleetAssignment.ORBIT_PASSIVE, origin, 1f, "Preparing for relocation");
        addBifrostLeg(active, origin.getStarSystem(), target.getStarSystem());
        active.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, target, 10000f,
                destination == null ? "Standing down: no operational base" : "Relocating to " + destination.getName());
    }

    public static boolean connectedByBifrost(StarSystemAPI a, StarSystemAPI b) {
        if (a == null || b == null || sameSystem(a, b) || !Global.getSettings().getModManager().isModEnabled("aotd_vok")) return false;
        BifrostMegastructure mega = BifrostMegastructureManager.getInstance().getMegastructure();
        return mega != null && mega.areStarSystemsConnected(a, b)
                && mega.getSectionEntityInStarSystem(a) != null && mega.getSectionEntityInStarSystem(b) != null;
    }

    private static void addBifrostLeg(final CampaignFleetAPI fleet, StarSystemAPI a, StarSystemAPI b) {
        if (!connectedByBifrost(a, b)) return;
        BifrostMegastructure mega = BifrostMegastructureManager.getInstance().getMegastructure();
        final SectorEntityToken origin = mega.getSectionEntityInStarSystem(a);
        final SectorEntityToken target = mega.getSectionEntityInStarSystem(b);
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, origin, 10000f, "Taking course towards Bifrost Gate", new Script() {
            @Override
            public void run() {
                if (!fleet.isAlive()) return;
                float distance = Misc.getDistanceLY(origin, target);
                if (origin.getCustomPlugin() instanceof BiFrostGateEntity plugin) plugin.showBeingUsed(distance);
                if (target.getCustomPlugin() instanceof BiFrostGateEntity plugin) plugin.showBeingUsed(distance);
                Global.getSector().doHyperspaceTransition(fleet, origin, new JumpPointAPI.JumpDestination(target, null), 2f);
            }
        });
    }

    public static void reportTransferDespawn(BasePatrolFleet data, CampaignFleetAPI fleet,
                                             CampaignEventListener.FleetDespawnReason reason) {
        if (!data.isTransferStarted() || data.getRelocationFleet() != fleet) return;
        data.setRelocationFleet(null);
        if (reason == CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION) finishTransfer(data);
        // Destroyed/offscreen transfers finish on the existing travel clock, as before.
    }

    private static void finishTransfer(BasePatrolFleet data) {
        data.setTiedTo(data.getTransferDestination());
        data.setTransferDestination(null);
        data.setRelocationFleet(null);
        data.setTransferStarted(false);
        data.setTransitOriginSystem(null);
        data.setInTransit(false);
        if (data.getAssignedBase() != null && !sameMarket(data.getTiedTo(), data.getAssignedBase()) && !data.isGrounded()) data.setInTransit(true);
    }
}
