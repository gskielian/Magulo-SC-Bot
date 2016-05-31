import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

//TODO clean up code
//TODO scout
//TODO get new base
//TODO frequency generalization
//TODO calculate probable location of enemy base
//TODO attack probable enemy base
//TODO defend base with everything

public class TestBot1 extends DefaultBWListener {
	private Mirror mirror = new Mirror();

	private Game game;
	private Player self;
	
	//game speed options
	private final int fastest = 0;
	private final int fast = 1;
	private final int medium = 2;

	private boolean attack_flag = false;
	private boolean scout_flag = false;
	private int last_attacked_base;
	private Position cc_position;
	private Position base_position;
	private int number_of_base_positions;
	private int base_scouting;
	private int base_attacking;
	
	//datastructures for SCV categories
	private Deque<Unit> BuilderSCVs = new ArrayDeque<Unit>();
	private Deque<Unit> GathererSCVs = new ArrayDeque<Unit>();
	private Deque<Unit> ScoutSCVs = new ArrayDeque<Unit>();
	
	
	private	int time_last_defend = 0;

	public void run() {
		mirror.getModule().setEventListener(this);
		mirror.startGame();
	}

	private void analyzeMap() {
		// Use BWTA to analyze map
		// This may take a few minutes if the map is processed first time!
		System.out.println("Analyzing map...");
		System.out.println("Please wait, this may take a few minutes if processing for the first time");
		BWTA.readMap();
		BWTA.analyze();
		number_of_base_positions=BWTA.getBaseLocations().size();
		System.out.println("Map data ready");
	}

	@Override
	public void onStart() {

		game = mirror.getGame();
		self = game.self();

		//this step may take a while for new maps
		analyzeMap();

		//fastest, fast, medium
		game.setLocalSpeed(fastest);


		for (Unit myUnit : self.getUnits()) {

			if (myUnit.getType() == UnitType.Terran_Command_Center && self.minerals() >= 50) {
				cc_position = myUnit.getPosition();
			}
		}
	}
	
	private Position attackNextBasePosition(){
		return BWTA.getBaseLocations().get((base_attacking)%number_of_base_positions).getPosition();
	}

	private Position scoutNextBasePosition(){
		return BWTA.getBaseLocations().get((base_scouting)%number_of_base_positions).getPosition();
	}

	@Override
	public void onUnitCreate(Unit unit) {
		System.out.println("New unit discovered " + unit.getType());
		if (unit.getType().isWorker()) {
			GathererSCVs.push(unit);
		}
	}

	private int countWorkers() {
		int number_of_workers = 0;
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType().isWorker()) {
				number_of_workers++;
			}
		}
		return number_of_workers;
	}

	private int countMarines() {
		int number_of_marines = 0;
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType() == UnitType.Terran_Marine) {
				number_of_marines++;
			}
		}
		return number_of_marines;
	}

	public int countBarracks() {
		int number_of_barracks = 0;
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType() == UnitType.Terran_Barracks) {
				number_of_barracks++;
			}
		}
		return number_of_barracks;
	}

	private void attackNextBase() {
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType() == UnitType.Terran_Marine) {
				myUnit.attack(attackNextBasePosition());
			}
		}
		base_attacking++;
	}
	private void defend(Position position){
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType() == UnitType.Terran_Marine) {
				myUnit.attack(position);
			}
		}
		time_last_defend = this.game.elapsedTime();
	}

	private void scoutNextBase() {
		//remove all scvs who can't move
		if (ScoutSCVs.size() > 0) {
			for (Iterator<Unit> itr = ScoutSCVs.descendingIterator(); itr.hasNext();) {
				if (!itr.next().canMove()) {
					ScoutSCVs.pop();
				}
			}
		}

		//if no scvs in group, add one
		if (ScoutSCVs.isEmpty()) {
			for (Unit myUnit : self.getUnits()) {
				if (myUnit.getType() == UnitType.Terran_SCV && myUnit.canMove()) {
					ScoutSCVs.add(myUnit);
					break;
				}
			}
		}
	 ScoutSCVs.peekLast().move(scoutNextBasePosition());
     base_scouting++;
	}

	@Override
	public void onFrame() {

		StringBuilder units = new StringBuilder("My units:\n");

		//build buildings
		SupplyDepots();
		Barracks();


		if (this.game.elapsedTime() % 200 == 0) {
			attack_flag = true;
		}
		if (attack_flag == true && this.game.elapsedTime() % 200 != 0) {
			attackNextBase();
			attack_flag = false;
		}
/*
		if (this.game.elapsedTime() % 200 == 0) {
			scout_flag = true;
		}
		if (scout_flag == true && this.game.elapsedTime() % 200 != 0) {
			scoutNextBase();
			scout_flag = false;
		}
		*/

		// iterate through my units
		for (Unit myUnit : self.getUnits()) {
			units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");

			// if there's enough minerals, train an SCV
			if (myUnit.getType() == UnitType.Terran_Command_Center && self.minerals() >= 50 && countWorkers() < 24) {
				myUnit.train(UnitType.Terran_SCV);
			}

			// if there's enough scv's and minerals, train a marine
			if (myUnit.getType() == UnitType.Terran_Barracks && self.minerals() >= 50
					&& (self.supplyTotal() - self.supplyUsed()) > 2) {
				myUnit.train(UnitType.Terran_Marine);
			}

			 if(this.game.elapsedTime() - time_last_defend > 100 && myUnit.isUnderAttack()){
				defend(myUnit.getPosition());
			 }
			idle_workers_to_minerals(myUnit);

		}

		// draw my units on screen
		game.drawTextScreen(10, 25, units.toString());
	}

	// helper methods
	public void idle_workers_to_minerals(Unit myUnit) {
		// if it's a worker and it's idle, send it to the closest mineral patch
		if (myUnit.getType().isWorker() && myUnit.isIdle()) {
			Unit closestMineral = null;

			// find the closest mineral
			for (Unit neutralUnit : game.neutral().getUnits()) {
				if (neutralUnit.getType().isMineralField()) {
					if (closestMineral == null
							|| myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
						closestMineral = neutralUnit;
					}
				}
			}

			// if a mineral patch was found, send the worker to gather it
			if (closestMineral != null) {
				myUnit.gather(closestMineral, false);
			}
		}
	}

	// Returns a suitable TilePosition to build a given building type near
	// specified TilePosition aroundTile, or null if not found. (builder
	// parameter is our worker)
	public TilePosition getBuildTile(Unit builder, UnitType buildingType, TilePosition aroundTile) {
		TilePosition ret = null;
		int maxDistance = 5;
		int stopDistance = 80;

		int busy_workers = 0;
		for (Iterator<Unit> itr = BuilderSCVs.iterator(); itr.hasNext();) {
			if (itr.next().isConstructing()) {
				busy_workers++;
			}
		}

		if (busy_workers < 3) {
			// Refinery, Assimilator, Extractor
			if (buildingType.isRefinery()) {
				for (Unit n : game.neutral().getUnits()) {
					if ((n.getType() == UnitType.Resource_Vespene_Geyser)
							&& (Math.abs(n.getTilePosition().getX() - aroundTile.getX()) < stopDistance)
							&& (Math.abs(n.getTilePosition().getY() - aroundTile.getY()) < stopDistance))
						return n.getTilePosition();
				}
			}

			for (int maxDist = maxDistance; (maxDist < stopDistance) && (ret == null); maxDist += 2) {
				for (int i = aroundTile.getX() - maxDist; i <= aroundTile.getX() + maxDist; i++) {
					for (int j = aroundTile.getY() - maxDist; j <= aroundTile.getY() + maxDist; j++) {
						if (game.canBuildHere(new TilePosition(i, j), buildingType, builder, false)) {

							// units that are blocking the tile
							boolean unitsInWay = false;
							for (Unit u : game.getAllUnits()) {
								if (u.getID() == builder.getID())
									continue;
								if ((Math.abs(u.getTilePosition().getX() - i) < 4)
										&& (Math.abs(u.getTilePosition().getY() - j) < 4))
									unitsInWay = true;
							}
							if (!unitsInWay) {
								return new TilePosition(i, j);
							}
						}
					}
				}
			}

			if (ret == null)
				game.printf("Unable to find suitable build position for " + buildingType.toString());
			return ret;
		}
		return ret;
	}

	// TODO create an array of SCV objects -- this way we don't have to loop
	// each time, and we can have stacks manage them
	// TODO basically this will make it so that the SCV's have names
	public void SupplyDepots() {
		while (!BuilderSCVs.isEmpty() && !BuilderSCVs.peekLast().exists()) {
			BuilderSCVs.removeLast();
		}
		// if we're running out of supply and have enough minerals ...
		if ((((float) (self.supplyTotal() - self.supplyUsed())) / ((float) (self.supplyTotal())) < 0.10)
				&& (self.minerals() >= 100)) {
			// iterate over units to find a worker
			if (GathererSCVs.size() > 0 && BuilderSCVs.size() < 3) {
				BuilderSCVs.addLast(GathererSCVs.removeLast());
			}
			System.out.println("BuilderSCVs " + BuilderSCVs.toString());
			System.out.println("Gatherer SCvs" + GathererSCVs.toString());
			TilePosition buildTile = getBuildTile(BuilderSCVs.peekLast(), UnitType.Terran_Supply_Depot,
					self.getStartLocation());
			if (buildTile != null) {
				BuilderSCVs.peekLast().build(UnitType.Terran_Supply_Depot, buildTile);
				BuilderSCVs.addFirst(BuilderSCVs.removeLast());
			}
		}
	}

	public void Barracks() {
		while (!BuilderSCVs.isEmpty() && !BuilderSCVs.peekLast().exists()) {
			BuilderSCVs.removeLast();
		}
		// if we're running out of supply and have enough minerals ...
		if (countBarracks() < 4 && self.minerals() >= 150) {
			// iterate over units to find a worker
			if (GathererSCVs.size() > 0 && BuilderSCVs.size() < 3) {
				BuilderSCVs.addLast(GathererSCVs.removeLast());
			}
			System.out.println("BuilderSCVs " + BuilderSCVs.toString());
			System.out.println("Gatherer SCvs" + GathererSCVs.toString());
			TilePosition buildTile = getBuildTile(BuilderSCVs.peekLast(), UnitType.Terran_Barracks,
					self.getStartLocation());
			if (buildTile != null) {
				BuilderSCVs.peekLast().build(UnitType.Terran_Barracks, buildTile);
				BuilderSCVs.addFirst(BuilderSCVs.removeLast());
			}
		}
	}

	public static void main(String[] args) {
		new TestBot1().run();
	}
}