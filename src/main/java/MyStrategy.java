import model.*;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Queue;
import java.util.Random;

public final class MyStrategy implements Strategy {
	
    private final Random random = new Random();
    private final boolean IN_SHOOTING_RANGE = true;
    private final boolean IN_VISIBLE_RANGE = false;
    private static HashMap<TrooperType, Point> pointsToMove = new HashMap<TrooperType, Point>();
    private static Point globalPointToAttack = null;
    private static Point medicMovePoint;
    private static Point soldierMovePoint;
    private static Point commanderMovePoint;
    private static Trooper leader = null;
    private static boolean mapWithDiffResp = false;
    private static boolean mapWithDiffRespSuccess = false;
    private static int wayLength = 0;
    private static CellType[][] myMapCells = null;
    enum Area {LEFT_BOTTOM, LEFT_TOP, RIGHT_BOTTOM, RIGHT_TOP};
    
    @Override
    public void move(Trooper self, World world, Game game, Move move) {
    	if(myMapCells == null){
    		myMapCells = world.getCells();
    		if(isLocalRunnerMap(world)){
    			myMapCells[0][11] = myMapCells[0][8] = 
    					myMapCells[world.getWidth() - 1][11] = 
    							myMapCells[world.getWidth() - 1][8] = CellType.LOW_COVER;
    		}
    	}
    	mapWithDiffResp = false;
    	if(world.getMoveIndex() < 4 && isMapWithDifferentRespawns(world)){
    		mapWithDiffResp = true;
    	}
        if (self.getActionPoints() < game.getStandingMoveCost()) {
            return;
        }
        Trooper enemy = getEnemy(self, world, IN_SHOOTING_RANGE, true);
        //UseMedikit for each other
        if(self.getHitpoints() <= self.getMaximalHitpoints() / 2 &&
        		self.isHoldingMedikit() && self.getActionPoints() >= game.getMedikitUseCost()) {
        	move.setAction(ActionType.USE_MEDIKIT);
        	move.setDirection(Direction.CURRENT_POINT);
        }
        //Wait for a medic
        else if(self.getHitpoints() < self.getMaximalHitpoints() && 
        		enemy == null && 
        		self.getType() != TrooperType.FIELD_MEDIC &&
        		getMyMedic(world) != null && getMyMedic(world).getHitpoints() > 0){
        	move.setAction(ActionType.END_TURN);
        	move.setDirection(Direction.CURRENT_POINT);
        }
        //Heal teammate near self by medikit
        else if(getVeryInjuryTeammateNear(self, world) != null &&  
        		self.getType() != TrooperType.FIELD_MEDIC &&
        		self.getActionPoints() >= game.getMedikitUseCost() &&
        		self.isHoldingMedikit()){
        	move.setAction(ActionType.USE_MEDIKIT);
        	move.setX(getVeryInjuryTeammateNear(self, world).getX());
        	move.setY(getVeryInjuryTeammateNear(self, world).getY());
        }
        //Heal teammate if he is near the medic
        else if(self.getType() == TrooperType.FIELD_MEDIC && 
        	getInjuryTeammateNear(self, world) != null &&
        	self.getActionPoints() >= game.getFieldMedicHealCost()){
        	move.setAction(ActionType.HEAL);
        	move.setX(getInjuryTeammateNear(self, world).getX());
        	move.setY(getInjuryTeammateNear(self, world).getY());
        	System.out.println("HEAL");
        }
        //Go to the injured teammate
        else if(self.getType() == TrooperType.FIELD_MEDIC &&
        		getInjuryTeammate(self, world) != null &&
        		self.getActionPoints() >= game.getStandingMoveCost() && 
        		(enemy == null || 
        		(enemy != null && 
        		!world.isVisible(self.getShootingRange(), self.getX(), self.getY(), 
        				self.getStance(), enemy.getX(), enemy.getY(), enemy.getStance())))){
        	setPointToMove(self, new Point(getInjuryTeammate(self, world).getX(), getInjuryTeammate(self, world).getY()));
        	move.setAction(ActionType.MOVE);
        	move.setDirection(bfs(medicMovePoint.x, medicMovePoint.y, world, self, true));
        }
        //Medic heal himself
        else if(self.getType() == TrooperType.FIELD_MEDIC &&
        		self.getActionPoints() >= game.getFieldMedicHealCost() && 
                (enemy != null && 
                self.getHitpoints() < 2.0 * self.getMaximalHitpoints() / 3 &&
                !world.isVisible(self.getShootingRange(), self.getX(), self.getY(), 
                		self.getStance(), enemy.getX(), enemy.getY(), enemy.getStance()) ||
                		enemy == null && self.getHitpoints() < self.getMaximalHitpoints())){
        	move.setAction(ActionType.HEAL);
        	move.setDirection(Direction.CURRENT_POINT);
        } else if(enemy != null || (getEnemy(self, world, IN_SHOOTING_RANGE, false)) != null && 
        		self.getActionPoints() < self.getShootCost() && 
        		self.getActionPoints() >= game.getStanceChangeCost() &&
        		self.getStance() != TrooperStance.PRONE){
        	attackEnemy(self, enemy, game, move);
        } else if(self.getActionPoints() >= game.getStandingMoveCost()){
        	moveTo(self, world, move);
        }
    }
    
    private Trooper getEnemy(Trooper self, World world, boolean inShootingRange,
    		boolean isSelfEnemy){
    	ArrayList<Trooper> enemies = new ArrayList<Trooper>();
    	for(Trooper trooper : world.getTroopers()){
        	if(!trooper.isTeammate() && 
        			world.isVisible(
        					inShootingRange ? self.getShootingRange() :
        						self.getVisionRange(), 
        					self.getX(), self.getY(), 
        					self.getStance(), 
        					trooper.getX(), trooper.getY(),
        					trooper.getStance()
        			) && (self.getActionPoints() >= self.getShootCost() ||
        			!isSelfEnemy)
        	  ){
        		enemies.add(trooper);
        	}
        }
    	if(enemies.isEmpty()){
    		return null;
    	}
    	Trooper resEnemy = null;
    	for(Trooper enemy : enemies){
    		if(self.getActionPoints() / self.getShootCost() * self.getDamage(self.getStance()) >= 
    				enemy.getHitpoints()){
    			resEnemy = enemy;
    		}
    	}
    	if(resEnemy == null){
    		Collections.sort(enemies, new Comparator<Trooper>() {
    			@Override
    			public int compare(Trooper o1, Trooper o2) {
    				return o2.getHitpoints() - o1.getHitpoints();
    			}
			});
    		resEnemy = enemies.get(0);
    	}
    	return resEnemy;
    }
    
    private void attackEnemy(Trooper self, Trooper enemy, Game game, Move move){
    	if(self.getActionPoints() < self.getShootCost() &&
    			self.getActionPoints() >= game.getStanceChangeCost()){
    		move.setAction(ActionType.LOWER_STANCE);
    		move.setDirection(Direction.CURRENT_POINT);
    		return;
    	}
    	if(self.isHoldingFieldRation() && 
    			self.getActionPoints() <= 8.0){
    		move.setAction(ActionType.EAT_FIELD_RATION);
    		move.setDirection(Direction.CURRENT_POINT);
    		return;
    	}
    	if(self.isHoldingGrenade() && 
    			self.getActionPoints() >= game.getGrenadeThrowCost() &&
    			self.getDistanceTo(enemy) <= game.getGrenadeThrowRange()){
    		move.setAction(ActionType.THROW_GRENADE);
    		System.out.println("It's grenade!");
    	} else if(self.getActionPoints() >= self.getShootCost()){
    		move.setAction(ActionType.SHOOT);
    	}
    	move.setX(enemy.getX());
    	move.setY(enemy.getY());
    }
    
    private void moveTo(Trooper self, World world, Move move){
    	leader = getLeader(world);
    	if(self.getStance() != TrooperStance.STANDING){
    		move.setAction(ActionType.RAISE_STANCE);
    		move.setDirection(Direction.CURRENT_POINT);
    		return;
    	}
    	if(globalPointToAttack == null){
    		globalPointToAttack = getGlobalPointToAttack(getMyArea(self, world), world);
    	}
    	if(!pointsToMove.containsKey(self.getType())){
    		pointsToMove.put(self.getType(), globalPointToAttack);
    	}
    	if(self.getX() == pointsToMove.get(self.getType()).x &&
        		self.getY() == pointsToMove.get(self.getType()).y ||
        		self.getDistanceTo(pointsToMove.get(self.getType()).x, pointsToMove.get(self.getType()).y) == 1 &&
        		isTeammateTrooperInCell(pointsToMove.get(self.getType()), world, new Point(-1, -1))){
    		if(globalPointToAttack.equals(new Point(self.getX(), self.getY()))){
    			globalPointToAttack = getGlobalPointToAttack(getMyArea(self, world), world);
    		}
    		pointsToMove.put(self.getType(), globalPointToAttack);
    	}
    	if(self.getType() != leader.getType()){
    		if(!pointsToMove.containsKey(leader)){
    			pointsToMove.put(leader.getType(), globalPointToAttack);
    		}
    		pointsToMove.put(self.getType(), new Point(leader.getX(), leader.getY()));
    	}
    	Trooper teammateEnemy = getTeammateEnemy(self, world);
    	boolean isTeammateEnemy = false;
    	if(teammateEnemy != null){
    		isTeammateEnemy = true;
    		pointsToMove.put(self.getType(), new Point(teammateEnemy.getX(), teammateEnemy.getY()));
    	}
    	Bonus bonus = getBonusToMove(self, getBonusesNear(self, world, 3));
    	if(self.getType() != leader.getType() && self.getDistanceTo(leader) < 2.0 &&
    			!isTeammateEnemy && bonus == null){
    		move.setAction(ActionType.END_TURN);
    		move.setDirection(Direction.CURRENT_POINT);
    		return;
    	}
    	if(bonus != null && !isTeammateEnemy || getBonusToMove(self, getBonusesNear(self, world, 1)) != null){
    		if(bonus == null){
    			bonus = getBonusToMove(self, getBonusesNear(self, world, 1));
    		}
    		pointsToMove.put(self.getType(), new Point(bonus.getX(), bonus.getY()));
    	}
    	if(self.getType() == leader.getType() && !isTeammateEnemy && bonus == null && !mapWithDiffResp){
    		for(Trooper trooper : getTeammates(self, world)){
        		if(self.getDistanceTo(trooper) > 4.0){
        			move.setAction(ActionType.END_TURN);
        			move.setDirection(Direction.CURRENT_POINT);
        			return;
        		}
        	}
    	}
    	if(self.getType() != leader.getType() &&
    			pointsToMove.get(self.getType()).x == leader.getX() &&
    			pointsToMove.get(self.getType()).y == leader.getY() &&
    			!isLeaderCanGoing(world)){
    		pointsToMove.put(self.getType(), globalPointToAttack);
    	}
    	Direction direction = bfs(
    			pointsToMove.get(self.getType()).x, pointsToMove.get(self.getType()).y, world, self,
    			self.getDistanceTo(leader) <= 4.0 ? true : false);
    	if(bfs(
    			pointsToMove.get(self.getType()).x, pointsToMove.get(self.getType()).y, world, self,
    			true) != bfs(
    	    			pointsToMove.get(self.getType()).x, pointsToMove.get(self.getType()).y, world, self,
    	    			false) && pointsToMove.get(self.getType()).equals(new Point(leader.getX(), leader.getY()))){
    		direction = bfs(
	    			pointsToMove.get(self.getType()).x, pointsToMove.get(self.getType()).y, world, self,
	    			false);
    	}
    	move.setAction(ActionType.MOVE);
    	move.setDirection(direction);
    }
    
    private Trooper getTeammateEnemy(Trooper self, World world){
    	for(Trooper trooper : world.getTroopers()){
    		if(trooper.isTeammate() && 
    				trooper.getType() != self.getType() && 
    				getEnemy(trooper, world, IN_SHOOTING_RANGE, false) != null){
    			return getEnemy(trooper, world, IN_SHOOTING_RANGE, false);
    		}
    	}
    	for(Trooper trooper : world.getTroopers()){
    		if(trooper.isTeammate() && 
    				trooper.getType() != self.getType() && 
    				getEnemy(trooper, world, IN_VISIBLE_RANGE, false) != null){
    			return getEnemy(trooper, world, IN_VISIBLE_RANGE, false);
    		}
    	}
    	return null;
    }
       
    private void setPointToMove(Trooper self, Point point){
    	switch(self.getType()){
    		case FIELD_MEDIC:
    			medicMovePoint = point;
    			break;
    		case COMMANDER:
    			commanderMovePoint = point;
    			break;
    		case SOLDIER:
    			soldierMovePoint = point;
    			break;
    	}
    }
    
    private Direction bfs(int x, int y, World world, Trooper self, boolean considerTeammateAsObstacle){
    	try {
	    	Queue<Point> q = new ArrayDeque<Point>();
	    	q.add(new Point(self.getX(), self.getY()));
	    	boolean[][] used = new boolean[world.getWidth()][world.getHeight()];
	    	int[][] d = new int[world.getWidth()][world.getHeight()];
	    	Point[][] p = new Point[world.getWidth()][world.getHeight()];
	    	used[self.getX()][self.getY()] = true;
	    	p[self.getX()][self.getY()] = new Point(-1, -1);
	    	boolean isWayFound = false;
	    	while (!q.isEmpty() && !isWayFound) {
	    		Point currentPoint = q.poll();
	    		if(used[x][y]){
	    			break;
	    		}
				for(int xx = currentPoint.x - 1; xx <= currentPoint.x + 1; xx += 2){
						if(isValidFreeCell(world, xx, currentPoint.y, 
								new Point(x, y), considerTeammateAsObstacle) && 
								!used[xx][currentPoint.y]){
							used[xx][currentPoint.y] = true;
							q.add(new Point(xx, currentPoint.y));
							d[xx][currentPoint.y] = d[currentPoint.x][currentPoint.y] + 1;
							p[xx][currentPoint.y] = currentPoint; 
						}
				}
				for(int yy = currentPoint.y - 1; yy <= currentPoint.y + 1; yy += 2){
					if(isValidFreeCell(world, currentPoint.x, yy, 
							new Point(x, y), considerTeammateAsObstacle) && 
						!used[currentPoint.x][yy]){
						used[currentPoint.x][yy] = true;
						q.add(new Point(currentPoint.x, yy));
						d[currentPoint.x][yy] = d[currentPoint.x][currentPoint.y] + 1;
						p[currentPoint.x][yy] = currentPoint; 
					}
				}	
	    	}
	    	if(!used[x][y]){
	    		return bfs(x, y, world, self, false);
	    	}
	    	Point currentPoint = null;
	    	Point prevPoint = new Point(x, y);
	    	do {
	    		prevPoint = (currentPoint != null ?
	    				currentPoint : new Point(x, y));
	    		currentPoint = (
	    				 currentPoint != null ?
	    				 p[currentPoint.x][currentPoint.y] : 
	    				 new Point(x, y)
	    				 ); 
	    		wayLength++;
	    	} while(!p[currentPoint.x][currentPoint.y].equals(new Point(-1, -1)));
	    	
	    	if(prevPoint.x == self.getX() + 1){
	    		return Direction.EAST;
	    	} else if (prevPoint.x == self.getX() - 1){
	    		return Direction.WEST;
	    	} else if(prevPoint.y == self.getY() + 1){
	    		return Direction.SOUTH;
	    	} else {
	    		return Direction.NORTH;
	    	}
    	} catch (NullPointerException e){
    		return bfs(x, y, world, self, false);
    	}
    }
    
	private boolean isValidFreeCell(
			World world, int x, int y, 
			Point dest, boolean considerTeammateAsObstacle
			){
		if(considerTeammateAsObstacle){
			if(x >= 0 && x < world.getWidth() &&
					y >= 0 && y < world.getHeight() &&
					myMapCells[x][y] == CellType.FREE && 
					!isTeammateTrooperInCell(new Point(x, y), world, dest)){
				return true;
			}
			return false;
		} else {
			if(x >= 0 && x < world.getWidth() &&
					y >= 0 && y < world.getHeight() &&
					myMapCells[x][y] == CellType.FREE){
				return true;
			}
			return false;
		}
		
	}

	private Area getMyArea(Trooper self, World world){
		if(self.getX() < world.getWidth() / 2){
			if(self.getY() < world.getHeight() / 2){
				return Area.LEFT_TOP;
			} else 
				return Area.LEFT_BOTTOM;
		} else if(self.getY() < world.getHeight() / 2){
			return Area.RIGHT_TOP;
		} else 
			return Area.RIGHT_BOTTOM;
	}
	
	private Point getGlobalPointToAttack(Area area, World world){
		if(area == Area.LEFT_BOTTOM){
			return random.nextBoolean() ? 
					new Point(1, 1) : 
					new Point(world.getWidth() - 2, 1);
		} else if(area == Area.LEFT_TOP){
			return random.nextBoolean() ? 
					new Point(1, world.getHeight() - 2) : 
					new Point(world.getWidth() - 2, world.getHeight() - 1);
		} else if(area == Area.RIGHT_TOP){
			return random.nextBoolean() ? 
					new Point(world.getWidth() - 2, world.getHeight() - 2) :
					new Point(1, world.getHeight() - 2);
		} else 
			return random.nextBoolean() ? 
					new Point(world.getWidth() - 2, 1) :
					new Point(1, world.getHeight() - 2);
	}
	
	private Trooper getInjuryTeammateNear(Trooper self, World world){
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() && 
					self.getDistanceTo(trooper) == 1 && 
					trooper.getHitpoints() < trooper.getMaximalHitpoints() &&
					trooper.getHitpoints() > 0){
				return trooper;
			}
		}
		return null;
	}
	
	private Trooper getVeryInjuryTeammateNear(Trooper self, World world){
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() && 
					self.getDistanceTo(trooper) == 1 && 
					trooper.getHitpoints() < trooper.getMaximalHitpoints() / 2 &&
					trooper.getHitpoints() > 0){
				return trooper;
			}
		}
		return null;
	}
	
	private Trooper getInjuryTeammate(Trooper self, World world){
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() && 
					self.getDistanceTo(trooper) > 1 && 
					trooper.getHitpoints() < trooper.getMaximalHitpoints() &&
					trooper.getHitpoints() > 0){
				return trooper;
			}
		}
		return null;
	}

	private Trooper getMyMedic(World world){
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() &&
					trooper.getType() == TrooperType.FIELD_MEDIC){
				return trooper;
			}
		}
		return null;
	}
	
	private Trooper getMyCommander(World world){
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() &&
					trooper.getType() == TrooperType.COMMANDER){
				return trooper;
			}
		}
		return null;
	}

	private boolean isTeammateTrooperInCell(Point point, World world, Point dest){
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() && 
					trooper.getX() == point.x &&
					trooper.getY() == point.y &&
					!point.equals(dest)){
				return true;
			}
		}
		return false;
	}

	private Trooper getLeader(World world){
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() &&
					trooper.getType() == TrooperType.COMMANDER &&
					trooper.getHitpoints() > 0){
				return trooper;
			}
		}
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() &&
					trooper.getType() == TrooperType.SOLDIER &&
					trooper.getHitpoints() > 0){
				return trooper;
			}
		}
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() &&
					trooper.getType() == TrooperType.FIELD_MEDIC &&
					trooper.getHitpoints() > 0){
				return trooper;
			}
		}
		return null;
	}

	private ArrayList<Trooper> getTeammates(Trooper self, World world){
		ArrayList<Trooper> result = new ArrayList<Trooper>();
		for(Trooper trooper : world.getTroopers()){
			if(self.getType() != trooper.getType() &&
					trooper.isTeammate() &&
					trooper.getHitpoints() > 0){
				result.add(trooper);
			}
		}
		return result;
	}

	private ArrayList<Bonus> getBonusesNear(Trooper self, World world, int radius){
		ArrayList<Bonus> result = new ArrayList<Bonus>();
		for(Bonus bonus : world.getBonuses()){
			if(self.getDistanceTo(bonus) <= radius){
				result.add(bonus);
			}
		}
		return result;
	}

	private Bonus getBonusToMove(Trooper self, ArrayList<Bonus> bonusesNear){
		Bonus result = null;
		HashMap<BonusType, Integer> numsOfBonusesNear = new HashMap<BonusType, Integer>();
		for(Bonus bonus : bonusesNear){
			if(numsOfBonusesNear.containsKey(bonus.getType())){
				numsOfBonusesNear.put(bonus.getType(), numsOfBonusesNear.get(bonus.getType()) + 1);
			} else {
				numsOfBonusesNear.put(bonus.getType(), 1);
			}
		}
		if(!self.isHoldingMedikit() && numsOfBonusesNear.containsKey(BonusType.MEDIKIT)){
			double minDistance = Integer.MAX_VALUE;
			for(Bonus bonus : bonusesNear){
				if(bonus.getType() == BonusType.MEDIKIT && 
						self.getDistanceTo(bonus) < minDistance){
					minDistance = self.getDistanceTo(bonus);
					result = bonus;
				}
			}
		} else if(!self.isHoldingGrenade() && numsOfBonusesNear.containsKey(BonusType.GRENADE)){
			double minDistance = Integer.MAX_VALUE;
			for(Bonus bonus : bonusesNear){
				if(bonus.getType() == BonusType.GRENADE && 
						self.getDistanceTo(bonus) < minDistance){
					minDistance = self.getDistanceTo(bonus);
					result = bonus;
				}
			}
		} else if(!self.isHoldingFieldRation() && numsOfBonusesNear.containsKey(BonusType.FIELD_RATION)){
			double minDistance = Integer.MAX_VALUE;
			for(Bonus bonus : bonusesNear){
				if(bonus.getType() == BonusType.FIELD_RATION && 
						self.getDistanceTo(bonus) < minDistance){
					minDistance = self.getDistanceTo(bonus);
					result = bonus;
				}
			}
		}
		return result;
	}

	private boolean isLeaderCanGoing(World world){
		ArrayList<Point> pointsAroundLeader = new ArrayList<Point>();
		pointsAroundLeader.add(new Point(leader.getX() + 1, leader.getY()));
		pointsAroundLeader.add(new Point(leader.getX() - 1, leader.getY()));
		pointsAroundLeader.add(new Point(leader.getX(), leader.getY() - 1));
		pointsAroundLeader.add(new Point(leader.getX(), leader.getY() + 1));
		ArrayList<Trooper> teammates = getTeammates(leader, world);
		CellType[][] cells = myMapCells;
		boolean result = false;
		for(Point point : pointsAroundLeader){
			if(isValidFreeCell(world, point.x, point.y, new Point(-1, -1), true)){
				result = true;
			}
		}
		return result;
	}

	private boolean isMapWithDifferentRespawns(World world){
		CellType[][] cells = world.getCells();
		boolean result = true;
		for(int x = 12; x <= 17; x++){
			for(int y = 7; y <= 12; y++){
				if(cells[x][y] == CellType.FREE){
					result = false;
				}
			}
		}
		return result;
	}

	private boolean isLocalRunnerMap(World world){
		CellType[][] cells = world.getCells();
		boolean result = true;
		for(int x = 13; x <= 16; x++){
			for(int y = 8; y <= 11; y++){
				if(x == 13 && y == 8 || x == 13 && y == 11
						|| x == 16 && y == 8 || x == 16 && y == 11){
					if(cells[x][y] != CellType.FREE){
						result = false;
					}
				} else if(cells[x][y] == CellType.FREE){
					result = false;
				}
			}
		}
		return result;
	}
}