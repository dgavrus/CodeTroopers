import model.*;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Queue;
import java.util.Random;

public final class MyStrategy implements Strategy {
    private final Random random = new Random();
    private static HashMap<TrooperType, Point> pointsToMove = new HashMap<TrooperType, Point>();
    private static Point globalPointToAttack = null;
    private static Point medicMovePoint;
    private static Point soldierMovePoint;
    private static Point commanderMovePoint;
    
    enum Area {LEFT_BOTTOM, LEFT_TOP, RIGHT_BOTTOM, RIGHT_TOP};
    
    @Override
    public void move(Trooper self, World world, Game game, Move move) {
        if (self.getActionPoints() < game.getStandingMoveCost()) {
            return;
        }
        Trooper enemy = getEnemy(self, world);
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
        else if(self.getHitpoints() < 2.0 * self.getMaximalHitpoints() / 3 &&
        		self.getType() == TrooperType.FIELD_MEDIC &&
        		self.getActionPoints() >= game.getFieldMedicHealCost() &&
        		(enemy == null || 
                (enemy != null && 
                !world.isVisible(self.getShootingRange(), self.getX(), self.getY(), 
                		self.getStance(), enemy.getX(), enemy.getY(), enemy.getStance())))){
        	move.setAction(ActionType.HEAL);
        	move.setDirection(Direction.CURRENT_POINT);
        } else if(enemy != null){
        	attackEnemy(self, enemy, game, move);
        } else if(self.getActionPoints() >= game.getStandingMoveCost()){
        	moveTo(self, world, move);
        }
    }
    
    private Trooper getEnemy(Trooper self, World world){
    	Trooper enemy = null;
    	for(Trooper trooper : world.getTroopers()){
        	if(!trooper.isTeammate() && 
        			world.isVisible(
        					self.getShootingRange(), 
        					self.getX(), self.getY(), 
        					self.getStance(), 
        					trooper.getX(), trooper.getY(),
        					trooper.getStance()
        			) && self.getActionPoints() >= self.getShootCost()
        	  ){
        		enemy = trooper;
        		break;
        	}
        }
    	return enemy;
    }
    
    private void attackEnemy(Trooper self, Trooper enemy, Game game, Move move){
    	if(self.isHoldingFieldRation()){
    		move.setAction(ActionType.EAT_FIELD_RATION);
    		move.setDirection(Direction.CURRENT_POINT);
    		return;
    	}
    	if(self.isHoldingGrenade() && 
    			self.getActionPoints() >= game.getGrenadeThrowCost() &&
    			self.getDistanceTo(enemy) <= game.getGrenadeThrowRange()){
    		move.setAction(ActionType.THROW_GRENADE);
    		System.out.println("It's grenade!");
    	} else {
    		move.setAction(ActionType.SHOOT);
    	}
    	move.setX(enemy.getX());
    	move.setY(enemy.getY());
    }
    
    private void moveTo(Trooper self, World world, Move move){
    	if(globalPointToAttack == null){
    		globalPointToAttack = getGlobalPointToAttack(getMyArea(self, world), world);
    	}
    	if(!pointsToMove.containsKey(self.getType())){
    		pointsToMove.put(self.getType(), globalPointToAttack);
    	}
    	if(self.getX() == pointsToMove.get(self.getType()).x &&
        		self.getY() == pointsToMove.get(self.getType()).y){
    		if(globalPointToAttack.equals(new Point(self.getX(), self.getY()))){
    			globalPointToAttack = getGlobalPointToAttack(getMyArea(self, world), world);
    		}
    		pointsToMove.put(self.getType(), globalPointToAttack);
    	}
    	Direction direction = bfs(
    			pointsToMove.get(self.getType()).x, pointsToMove.get(self.getType()).y, world, self, true);
    	move.setAction(ActionType.MOVE);
    	move.setDirection(direction);
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
					world.getCells()[x][y] == CellType.FREE && 
					!isTeammateTrooperInCell(new Point(x, y), world, dest)){
				return true;
			}
			return false;
		} else {
			if(x >= 0 && x < world.getWidth() &&
					y >= 0 && y < world.getHeight() &&
					world.getCells()[x][y] == CellType.FREE){
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
					new Point(0, 0) : 
					new Point(world.getWidth() - 1, 0);
		} else if(area == Area.LEFT_TOP){
			return random.nextBoolean() ? 
					new Point(0, world.getHeight() - 1) : 
					new Point(world.getWidth() - 1, world.getHeight() - 1);
		} else if(area == Area.RIGHT_TOP){
			return random.nextBoolean() ? 
					new Point(world.getWidth() - 1, world.getHeight() - 1) :
					new Point(0, world.getHeight() - 1);
		} else 
			return random.nextBoolean() ? 
					new Point(world.getWidth() - 1, 0) :
					new Point(0, world.getHeight() - 1);
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
}


