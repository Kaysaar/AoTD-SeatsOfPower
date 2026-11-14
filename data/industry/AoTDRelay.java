package data.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.ai.PatrolAssigmentAIV5;

import data.scripts.patrolfleet.managers.AoTDFactionPatrolsManager;
import data.scripts.patrolfleet.models.AoTDPatrolFleetData;
import data.scripts.patrolfleet.models.BasePatrolFleet;
import data.scripts.patrolfleet.utilis.PatrolFleetFactory;
import data.scripts.patrolfleet.utilis.PatrolAssignmentService;
import indevo.industries.relay.industry.MilitaryRelay;


public class AoTDRelay extends MilitaryRelay {
    @Override
    public void advance(float amount) {
        if (market.getFaction() == null || !market.getFaction().isPlayerFaction()) {
            super.advance(amount);
            return;
        }
        boolean disrupted = isDisrupted();
        if (!disrupted && wasDisrupted) {
            disruptionFinished();
        }
        wasDisrupted = disrupted;

//		if (disrupted) {
//			//if (DebugFlags.COLONY_DEBUG) {
//				String key = getDisruptedKey();
//				market.getMemoryWithoutUpdate().unset(key);
//			//}
//		}

        if (building && !disrupted) {
            float days = Global.getSector().getClock().convertToDays(amount);
            //DebugFlags.COLONY_DEBUG = true;
            if (DebugFlags.COLONY_DEBUG) {
                days *= 100f;
            }
            buildProgress += days;

            if (buildProgress >= buildTime) {
                finishBuildingOrUpgrading();
            }
        }

        if (Global.getSector().getEconomy().isSimMode()) return;

        if (!isFunctional()) return;

        float days = Global.getSector().getClock().convertToDays(amount);

//		float stability = market.getPrevStability();
//		float spawnRate = 1f + (stability - 5) * 0.2f;
//		if (spawnRate < 0.5f) spawnRate = 0.5f;

        float spawnRate = 1f;
        float rateMult = market.getStats().getDynamic().getStat(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).getModifiedValue();
        spawnRate *= rateMult*2.5f;

        if (Global.getSector().isInNewGameAdvance()) {
            spawnRate *= 3f;
        }

        float extraTime = 0f;
        if (returningPatrolValue > 0) {
            // apply "returned patrols" to spawn rate, at a maximum rate of 1 interval per day
            float interval = tracker.getIntervalDuration();
            extraTime = interval * days;
            returningPatrolValue -= days;
            if (returningPatrolValue < 0) returningPatrolValue = 0;
        }
        tracker.advance(days * spawnRate + extraTime);
        if (tracker.intervalElapsed()) {
//			if (market.isPlayerOwned()) {
//				System.out.println("ewfwefew");
//			}
//			if (market.getName().equals("Jangala")) {
//				System.out.println("wefwefe");
//			}
            String sid = getRouteSourceId();


            WeightedRandomPicker<BasePatrolFleet> picker = new WeightedRandomPicker<BasePatrolFleet>();

            for (BasePatrolFleet fleet : AoTDFactionPatrolsManager.getInstance().getAssignedFleetsForMarket(market)) {
                if (fleet.isValidToSpawn()) picker.add(fleet, fleet.getFPTaken());
            }


            if (picker.isEmpty()) return;

            BasePatrolFleet type = picker.pick(Misc.random);
            AoTDPatrolFleetData custom = new AoTDPatrolFleetData();
            custom.setId(type.getId());
            custom.setType(type.getPatrolType());

            RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(market);

            RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, this, custom);
            PatrolAssignmentService.registerRoute(type, route);
            extra.strength = (float) type.getFPTaken();
            extra.strength = Misc.getAdjustedStrength(extra.strength, market);


            float patrolDays = 35f + (float) Math.random() * 10f;
            route.addSegment(new RouteManager.RouteSegment(patrolDays, market.getPrimaryEntity()));
        }

    }
    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (!fleet.getMemoryWithoutUpdate().contains(PatrolAssignmentService.FLEET_ID_KEY)) {
            super.reportFleetDespawnedToListener(fleet, reason, param);
            return;
        }
        BasePatrolFleet data = PatrolAssignmentService.getFleetForEntity(fleet);
        if (data == null) return;
        if (reason == CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION
                && !PatrolAssignmentService.needsReturn(data) && data.getFPTaken() > 0) {
            returningPatrolValue += (float) fleet.getFleetPoints() / data.getFPTaken();
        }
        PatrolAssignmentService.reportPatrolDespawn(data, fleet, reason);
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteManager.RouteData route) {
        if (!(route.getCustom() instanceof AoTDPatrolFleetData custom)) return super.spawnFleet(route);
        BasePatrolFleet data = AoTDFactionPatrolsManager.getInstance().getFleet(custom.getId());
        if (!PatrolAssignmentService.canSpawnRoute(data, route)) return null;
        CampaignFleetAPI fleet = createPatrol(custom.getId(), route);
        if (fleet == null || fleet.isEmpty()) return null;
        fleet.getMemoryWithoutUpdate().set(PatrolAssignmentService.FLEET_ID_KEY, data.getId());
        fleet.getMemoryWithoutUpdate().set(PatrolAssignmentService.ROUTE_KEY, route);
        fleet.addEventListener(this);
        market.getContainingLocation().addEntity(fleet);
        fleet.setFacing((float) Math.random() * 360f);
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);
        fleet.addScript(new PatrolAssigmentAIV5(fleet, route));
        fleet.setTransponderOn(true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f);
        return fleet;
    }

    @Override
    public boolean shouldCancelRouteAfterDelayCheck(RouteManager.RouteData route) {
        if (route.getCustom() instanceof AoTDPatrolFleetData custom) {
            return !PatrolAssignmentService.canSpawnRoute(
                    AoTDFactionPatrolsManager.getInstance().getFleet(custom.getId()), route);
        }
        return super.shouldCancelRouteAfterDelayCheck(route);
    }
    public CampaignFleetAPI createPatrol(String id, RouteManager.RouteData route) {
        BasePatrolFleet fleets = AoTDFactionPatrolsManager.getInstance().getFleet(id);
        fleets.performReplacement();
        FleetParamsV3 params = new FleetParamsV3(
                market,
                null,
                market.getFactionId(),
                route == null ? null : route.getQualityOverride(),
                null,
                fleets.getFPTaken(), // combatPts
                0f, // freighterPts
                0f, // tankerPts
                0f, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0f // qualityMod
        );
        params.random = route.getRandom();
        CampaignFleetAPI fleet = PatrolFleetFactory.createFleetFromAssigned(fleets, params, market, params.random);
        if (fleet == null || fleet.isEmpty()) return null;

        if (!fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PATROLS_HAVE_NO_PATROL_MEMORY_KEY)) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
            if (fleets.getFPTaken() >= 50) {
                fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_CUSTOMS_INSPECTOR, true);
            }
        } else if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);

            // hidden pather and pirate bases
            // make them raid so there's some consequence to just having a colony in a system with one of those
            if (market != null && market.isHidden()) {
                fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);
            }
        }

        String postId = Ranks.POST_PATROL_COMMANDER;
        String rankId = Ranks.SPACE_COMMANDER;
        float fp = fleets.getFPTaken();
        if (fp >= 30) {
            rankId = Ranks.SPACE_LIEUTENANT;
        }
        if (fp >= 50) {
            rankId = Ranks.SPACE_COMMANDER;

        }
        if (fp >= 100) {
            rankId = Ranks.SPACE_CAPTAIN;
        }
        fleet.getCommander().setPostId(postId);
        fleet.getCommander().setRankId(rankId);

        return fleet;
    }
}
