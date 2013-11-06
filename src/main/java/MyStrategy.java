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
    private static ArrayList<TrooperType> start = new ArrayList<TrooperType>();
    private static Point pointToAttack;
    enum Area {LEFT_BOTTOM, LEFT_TOP, RIGHT_BOTTOM, RIGHT_TOP};
    
    @Override
    public void move(Trooper self, World world, Game game, Move move) {
        if (self.getActionPoints() < game.getStandingMoveCost()) {
            return;
        }
        removeDeadTrooper(world);
        Trooper enemy = getEnemy(self, world);
        if(self.getHitpoints() <= self.getMaximalHitpoints() / 2 &&
        		self.isHoldingMedikit() && self.getActionPoints() >= game.getMedikitUseCost()) {
        	move.setAction(ActionType.USE_MEDIKIT);
        	move.setDirection(Direction.CURRENT_POINT);
        } else if(self.getType() == TrooperType.FIELD_MEDIC && 
        	getInjuryTeammateNear(self, world) != null &&
        	self.getActionPoints() >= game.getFieldMedicHealCost()){
        	move.setAction(ActionType.HEAL);
        	move.setX(getInjuryTeammateNear(self, world).getX());
        	move.setY(getInjuryTeammateNear(self, world).getY());
        	System.out.println("HEAL");
        } else if(self.getHitpoints() <= self.getMaximalHitpoints() / 2 &&
        		self.getType() == TrooperType.FIELD_MEDIC &&
        		self.getActionPoints() >= game.getFieldMedicHealCost()){
        	move.setAction(ActionType.HEAL);
        	move.setDirection(Direction.CURRENT_POINT);
        } else if(enemy != null){
        	attackEnemy(self, enemy, game, move);
        } else moveTo(self, world, move);
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
    	if(!start.contains(self.getType())){
    		start.add(self.getType());
    		Area myArea = getMyArea(self, world);
        	pointToAttack = getPointToAttack(myArea, world);
        	System.out.println("HI1");
    	}
    	if(self.getX() == pointToAttack.x &&
        		self.getY() == pointToAttack.y){
    		System.out.println("HI2");
    		start.clear();
    	}
    	Direction direction = bfs(pointToAttack.x, pointToAttack.y, world, self);
    	move.setAction(ActionType.MOVE);
    	move.setDirection(direction);
    }
    
    private Direction bfs(int x, int y, World world, Trooper self){
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
					if(isValidFreeCell(world, xx, currentPoint.y) && !used[xx][currentPoint.y]){
						used[xx][currentPoint.y] = true;
						q.add(new Point(xx, currentPoint.y));
						d[xx][currentPoint.y] = d[currentPoint.x][currentPoint.y] + 1;
						p[xx][currentPoint.y] = currentPoint; 
					}
			}
			for(int yy = currentPoint.y - 1; yy <= currentPoint.y + 1; yy += 2){
				if(isValidFreeCell(world, currentPoint.x, yy) && !used[currentPoint.x][yy]){
					used[currentPoint.x][yy] = true;
					q.add(new Point(currentPoint.x, yy));
					d[currentPoint.x][yy] = d[currentPoint.x][currentPoint.y] + 1;
					p[currentPoint.x][yy] = currentPoint; 
				}
			}	
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
    }
    
	private boolean isValidFreeCell(World world, int x, int y){
		if(x >= 0 && x < world.getWidth() &&
				y >= 0 && y < world.getHeight() &&
				world.getCells()[x][y] == CellType.FREE){
			return true;
		}
		return false;
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
	
	private Point getPointToAttack(Area area, World world){
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
					new Point(0, 0);
	}
	
	private void removeDeadTrooper(World world){
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() && 
					start.contains(trooper.getType()) &&
					trooper.getHitpoints() == 0){
				start.remove(trooper);
			}
		}
	}
	
	private Trooper getInjuryTeammateNear(Trooper self, World world){
		for(Trooper trooper : world.getTroopers()){
			if(trooper.isTeammate() && 
					self.getDistanceTo(trooper) == 1 && 
					trooper.getHitpoints() < trooper.getMaximalHitpoints()){
				System.out.println(trooper.getType());
				return trooper;
			}
		}
		return null;
	}
	
}


