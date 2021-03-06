package com.me.footymarathon;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;

import com.badlogic.gdx.Input.Keys;

public class FootyMarathon implements ApplicationListener {
    
	public enum PlayerState {
		chasing,
		positioning,
		stunned,
		marking,
		dribbling,
		holding,
		moving,
		runningon,
		waiting,
		setpiecetaking,
		closingangle,
		diving,
		toball
	}
	
	public class Polar {
		float angle;
		float distance;
		
		public Polar(float angle, float distance) {
			this.angle = angle;
			this.distance = distance;
		}
	}
	
	public class DirScore implements Comparator<DirScore> {
		float angle;
		float score;
		
		public DirScore(float angle, float score) {
			this.angle = angle;
			this.score = score;
		}
		
		public int compare(DirScore d1, DirScore d2) {
			return (int) ((d2.score - d1.score) * 100);
		}
	}
	
public class Player implements Comparator<Player> {

		float SIZE = 32;
		float rotationSpeed = 20;
		float dirSpeed = 200;
		float curlSpeed = 100f;
		float runSpeed = 1;
		
		boolean possession = false;
		Vector2 position = new Vector2();
		Vector2 velocity = new Vector2();
		String label = "";
		boolean goalKeeper;
		float angle;
		float stateTime = 0;
		float kicking = 0;
		float diving = 0;
		float kickBuilder = 0;
		float tackling = 0;
		float stun = 0;
		float runon = 0;
		float controlSpeed = 550;
		int animDir = 0;
		float animTime = 0;
		int team;
		String name;
		Rectangle bounds = new Rectangle();
		Vector2 formation = new Vector2();
		ArrayList<Player> myTeam;
		ArrayList<Player> theirTeam;
		Polar[] myTeamPolar;
		Polar[] theirTeamPolar;
		Player calledFor = null;
		PlayerState state = null;
		
		public ArrayList<Player> getPlayerList(int team) {
			ArrayList<Player> relPlayers = new ArrayList<Player>(6);
			if (users != null) {
				for (User u: users) {
					if (u.team == team && !u.equals(this)) {
						relPlayers.add(u);
					}
				}
			}
			for (ComPlayer c: complayers) {
				if (c != null) {
					if (c.team == team && !c.equals(this)) {
						relPlayers.add(c);
					}
				}
			}
			Collections.sort(relPlayers, this);
			return relPlayers;
		}
		
		public void renderPlayer(float deltaTime) {
			TextureRegion frame = null;
			int offset = this.team * 8;
			if (this instanceof Goalkeeper) offset = 16;
			int dir = (int) (this.angle + 22.5) / 45 % 8;
			if (dir != this.animDir){
				if (animTime < masterTime - 0.15f) {
					animTime = masterTime;
					animDir = dir;
				}
				else dir = animDir;
			}
			if (this.tackling > 0 && this instanceof Goalkeeper) frame = playerFalls.get(dir + offset).getKeyFrame(30.2f);
			else if (this.kicking > 0 || this.tackling > 0) {
				frame = playerKicks.get(dir + offset).getKeyFrame(0);
			}
			else if (this.stun > 0) {
				frame = playerFalls.get(dir + offset).getKeyFrame(1.0f - this.stun);
			}
			else if (this.diving > 0) {
				dir = (int) (this.velocity.angle() + 22.5) / 45 % 8;
				frame = playerFalls.get(dir + offset).getKeyFrame(30.2f);
			}
			else if (this.velocity.len() < 0.5) {
				frame = playerStopped.get(dir + offset).getKeyFrame(0);
			}
			else {
				frame = playerRuns.get(dir + offset).getKeyFrame(this.stateTime);
			}
			if (this.position.dst(camera.position.x, camera.position.y) > 350) {
				Vector2 arrowVect = this.position.cpy().sub(camera.position.x, camera.position.y);
				Vector2 dispVect = arrowVect.cpy().nor().scl(350);
				batch.setColor(1 - this.team, 0, this.team, 1);
				batch.draw(arrow, camera.position.x + dispVect.x - 16, camera.position.y + dispVect.y - 16, 16, 16, 32, 32, 1.5f - ((arrowVect.len() - 250) / 2000), 1.5f - ((arrowVect.len() - 250) / 2000), arrowVect.angle());
				batch.setColor(1, 1, 1, 1);
				if (this instanceof User) {
					font.draw(batch, "P", camera.position.x + dispVect.x - 6, camera.position.y + dispVect.y + 8);
				}
			}
			else if (this instanceof User) font.draw(batch, "P", this.position.x - 8, this.position.y + 42);
			batch.draw(frame, this.position.x - this.SIZE/2, this.position.y - this.SIZE/2, this.SIZE/2, this.SIZE/2, this.SIZE, this.SIZE, 1f, 1f, 0);
			if (this instanceof ComPlayer) {
				if (this.possession) font.setColor(1, 0, 1, 1);
				font.draw(batch, this.label, this.position.x - font.getBounds(this.label).width/2, this.position.y + this.SIZE + 10);
				if (this.possession) font.setColor(0, 0, 0, 1); 
			}
		}
		
		public int compare(Player p1, Player p2) {
			return (int) (this.position.dst(p1.position) - this.position.dst(p2.position));
		}
	}
	
	public class User extends ComPlayer {

		boolean setPiece;
		float inputTime;
		float misControl;
		Vector2 dribbleTarget;
		
		public User(Vector2 position, int team, String name, Vector2 formation) {
			super(position, team, name, formation);
			this.state = null;
			this.setPiece = false;
			this.myTeamPolar = new Polar[teamSizes[this.team]];
			this.theirTeamPolar = new Polar[teamSizes[1 - this.team]];
			this.inputTime = 0;
			this.dribbleTarget = null;
			this.misControl = 0;
		}

		public void recalcPlayer(float deltaTime) {
			if (deltaTime == 0) return;
			this.stateTime += deltaTime * this.velocity.len();
			this.inputTime += deltaTime;
			this.myTeam = this.getPlayerList(this.team);
			this.theirTeam = this.getPlayerList(1 - this.team);
			for (Player p: this.myTeam) {
				this.myTeamPolar[this.myTeam.indexOf(p)] = new Polar(p.position.cpy().sub(this.position).angle(), p.position.dst(this.position));
			}
			for (Player p: this.theirTeam) {
				this.theirTeamPolar[this.theirTeam.indexOf(p)] = new Polar(p.position.cpy().sub(this.position).angle(), p.position.dst(this.position));
			}
		}
		
		public void updateUser(float deltaTime) {
			if(deltaTime == 0) return;
			float factor = 1 / (float) Math.sqrt(2);
			Vector2 dirTarget = new Vector2(1, 0).rotate(this.angle);
			// CHECK FOR MOUSE/POINTER CONTROL
			if (Gdx.input.isTouched()) {
				if (touchCount == 0) {
					touchStart = new Vector2(Gdx.input.getX(), screenHeight - Gdx.input.getY());
				}
				touchCount += deltaTime;
			}
			else if (touchCount > 0) {
				if (touchCount < 0.2) {
					Vector2 target = screenToWorld(touchStart);
					if (!this.possession && this.misControl < masterTime - USER_MISCONTROL_TIME) {
						if (target.dst(new Vector2(ball.position.x, ball.position.y)) < 50) {
							this.state = PlayerState.chasing;
							System.out.println("Chasing ball");
						}
						else {
							this.state = PlayerState.moving;
							this.posTarget = target;
							System.out.println("Moving user to " + this.posTarget + ", based on cam position of " + camera.position + " and touch at coords " + touchStart);
						}
					}
					else if (this.state == PlayerState.dribbling) {
						boolean usedUp = false;
						for (Player c: myTeam) {
							if (c.position.dst(target) < 50 && !c.equals(this)) {
								calledFor = c;
								usedUp = true;
							}
						}
						if (!usedUp) {
							System.out.println("Change dribble target");
							this.dribbleTarget = screenToWorld(touchStart);
						}
					}
				}
				else {
					Vector2 touchEnd = new Vector2(Gdx.input.getX(), screenHeight - Gdx.input.getY());
					Vector2 kickTarget = screenToWorld(touchEnd);
					Vector2 kickDir = kickTarget.cpy().sub(ball.position.x, ball.position.y).nor().scl(Math.max(Math.min(300, touchCount * 500), 700));
					ball.velocity = new Vector3(kickDir.x, kickDir.y, (float) Math.pow(kickDir.len(), 1.9)/(1200 + (float)Math.random() * 800));
					ball.curl = 0;
					this.kicking = 0.8f;
					this.possession = false;
					posPlayer = null;
					ball.possession = -1;
					lastTouch = this.team;
				}
				touchCount = 0;
				this.inputTime = 0;
				screenTouch = screenToWorld(touchStart);
			}
			// CHECK FOR KEYBOARD CONTROL
			if(Gdx.input.isKeyPressed(Keys.LEFT)) {
				if(Gdx.input.isKeyPressed(Keys.UP)) {
					dirTarget = new Vector2(-this.dirSpeed * factor, this.dirSpeed * factor);
				}
				else if(Gdx.input.isKeyPressed(Keys.DOWN)) {
			
					dirTarget = new Vector2(-this.dirSpeed * factor, -this.dirSpeed * factor);
				}
				else {
					dirTarget = new Vector2(-this.dirSpeed, 0);
				}
				this.state = null;
				this.inputTime = 0;
			}
			else if(Gdx.input.isKeyPressed(Keys.RIGHT)) {
				if(Gdx.input.isKeyPressed(Keys.UP)) {
					dirTarget = new Vector2(this.dirSpeed * factor, this.dirSpeed * factor);
				}
				else if(Gdx.input.isKeyPressed(Keys.DOWN)) {
					dirTarget = new Vector2(this.dirSpeed * factor, -this.dirSpeed * factor);
				}
				else {
					dirTarget = new Vector2(this.dirSpeed, 0);
				}
				this.state = null;
				this.inputTime = 0;
			}
			else {
				if(Gdx.input.isKeyPressed(Keys.UP)) {
					dirTarget = new Vector2(0, this.dirSpeed);
					this.state = null;
					this.inputTime = 0;
				}
				else if(Gdx.input.isKeyPressed(Keys.DOWN)) {
					dirTarget = new Vector2(0, -this.dirSpeed);
					this.state = null;
					this.inputTime = 0;
				}
			}
			if (this.velocity.len() > 0.1) this.angle = this.velocity.angle();
			if (Gdx.input.isKeyPressed(Keys.ANY_KEY)) {
				if (this.velocity.len() < 0.1) {
					this.velocity = new Vector2();
				}
				this.position.add(this.velocity.cpy().scl(deltaTime));
				// Impact on ball
				if (setPiece) {}
				else if (this.position.dst(ball.position.x, ball.position.y) < this.SIZE * CONTROL * 0.75 && ball.position.z < 100 && 
						this.velocity.cpy().add(new Vector2(ball.velocity.x, ball.velocity.y)).len() > this.controlSpeed && this.kicking <= 0) {	
					float impulse = this.velocity.cpy().add(new Vector2(ball.velocity.x, ball.velocity.y)).len();
					float restAngle = (2 * this.position.cpy().sub(new Vector2(ball.position.x, ball.position.y)).angle()) - new Vector2(ball.velocity.x, ball.velocity.y).angle() - 180;
					Vector3 axisVect = new Vector3(0, 0, 1).rotate((float)Math.random() * 30, (float)Math.random(), (float)Math.random(), 0);
					ball.velocity = new Vector3(impulse * RICOCHET_COEFFICIENT, 0, 0).rotate(axisVect, restAngle);
					lastTouch = this.team;
				}			
				else if (this.position.dst(ball.position.x, ball.position.y) < this.SIZE * CONTROL * 0.75 && this.stun <= 0 && this.kicking <= 0 && ball.position.z < 100) {
					Vector2 bestBallDir = this.position.cpy().add(new Vector2(dirTarget.cpy().nor().scl(SIZE + CONTROL)))
							.sub(new Vector2(ball.position.x, ball.position.y)).scl(1).limit(this.dirSpeed * this.runSpeed * 1.5f);
					ball.velocity.add(new Vector3(bestBallDir.x, bestBallDir.y, -ball.velocity.z * 0.5f));
					lastTouch = this.team;
				}
				if (new Vector2(this.position.x, this.position.y).dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.55
						&& this.kicking <= 0 && ball.position.z < 50  && !setPiece) {
					if (this.tackling <= 0 && (ball.possession != (1-this.team)) &&
							this.velocity.cpy().sub(ball.velocity.x, ball.velocity.y).len() < 500) {
						clearPossession();
						this.possession = true;
						this.runSpeed = DRIB_PEN;
						this.runon = 0;
						passedTo = null;
						ball.possession = this.team;
						posPlayer = this;
						ball.curl = 0;
						ball.rotation = 0;
						ball.velocity = new Vector3();
						lastTouch = this.team;
					}
					else if (this.tackling > 0) {
						ball.velocity.lerp(new Vector3(this.velocity.x, this.velocity.y, 2).scl(1.5f), 0.2f);
						if (posPlayer != null) {
							posPlayer.stun = 0.8f;
						}
						lastTouch = this.team;
					}
				}
				if (this.kicking > 0 || this.tackling > 0) this.velocity.scl(0.95f);
				if (this.stun > 0) this.velocity.scl(0.9f);
				this.kicking = Math.max(this.kicking - deltaTime, 0);
				this.tackling = Math.max(this.tackling - deltaTime, 0);
				this.stun = Math.max(this.stun - deltaTime, 0);					
			}
			else {
				dirTarget = new Vector2(0, 0);
				if (this.state != null) this.updatePlayer(deltaTime);
//				System.out.println("Speed: " + this.velocity.len() + ", target: " + this.dribbleTarget + ", current target: " + this.posTarget);
			}
			if (Gdx.input.isKeyPressed(Keys.Z)) {
				if ((this.possession || this.kickBuilder > 0) && this.tackling <= 0) {
					this.kickBuilder += deltaTime;
				}
				else if (this.tackling == 0 && this.stun == 0) {
					this.tackling = 0.7f;
					this.velocity = dirTarget.cpy().nor().scl(this.dirSpeed * 1.5f);
				}
				this.state = null;
			}
			else {
				if (this.kickBuilder > 0) {
					if (this.possession) {
						this.possession = false;
						this.kicking = Math.min(this.kickBuilder,  0.6f) + 0.4f;
						Vector2 kickDir = new Vector2(this.kicking, 0).rotate(this.angle);
						ball.velocity = new Vector3(kickDir.x, kickDir.y, (float) Math.pow(this.kicking / 2, 1.5)).scl(1000);
						ball.possession = -1;
						posPlayer = null;
					}
					this.kickBuilder = 0;
				}
			}
			if (Gdx.input.isKeyPressed(Keys.X)){
				if (this.possession) {
					float bestScore = 0;
					Vector2 bestVector = new Vector2(250,0).rotate(dirTarget.angle());
					Player passTarget = null;
					float passTime = 0;
					float midAngle = dirTarget.angle();
					float ang = midAngle - 30 + (float) (5 * Math.random());
					while (ang <= midAngle + 30) {
						for (Player p: this.myTeam) {
							Vector2 intersect = new Vector2();
							Intersector.intersectLines(new Vector2(ball.position.x, ball.position.y), new Vector2(ball.position.x, ball.position.y).add(new Vector2(100, 0).rotate(ang)), p.position, p.position.cpy().add(p.velocity), intersect);
							Vector2 passRequired = new Vector2(intersect.x - ball.position.x, intersect.y - ball.position.y);
							float time = (intersect.x - p.position.x) / p.velocity.x;
							float denom = 1 - (float) Math.exp(time * -GROUND_RESISTANCE);
							Vector2 passVector = passRequired.scl(GROUND_RESISTANCE/denom);
							if (time < 0.2f || time > 10 || passRequired.len() > 1000 || passRequired.len()/time < 100
									|| angCompare(passRequired.angle(), ang) > 10 || passVector.len() * Math.exp(-GROUND_RESISTANCE * time) > 500) continue;
							float newScore = 1 - (float) Math.pow(1 - 600/passVector.len(), 2);
							for (Player q: this.theirTeam) {
								if (between(this, intersect, q, 10)) newScore -= 1;
							}
							if (Math.abs(intersect.x) > 640 || Math.abs(intersect.y) > 1024) newScore -= 1;
							if (newScore > bestScore) {
								bestScore = newScore;
								bestVector = passVector.cpy();
								passTarget = p;
								passTime = time;
								passEnd = intersect;
								System.out.println(ang + ", " + newScore + ", " + intersect + ", " + passRequired.angle() + ", " + time);								
							}
						}
						ang += 5;
					}
					System.out.println("My angle: " + midAngle + ", pass angle: " + passEnd.cpy().sub(this.position).angle());
					ball.velocity = new Vector3(bestVector.x, bestVector.y, 0);
					this.possession = false;
					if (passTarget != null) {
						passTarget.runon = passTime + 0.5f;
					}
					posPlayer = null;
					this.kicking = 0.5f;
				}
				this.state = null;
			}
			if (Gdx.input.isKeyPressed(Keys.C)){
				if (ball.possession == this.team && !this.possession) {
					for (Player p: this.myTeam) {
						if (p.possession) p.calledFor = this;
					}
				}
			}
			if (this.kicking <= 0 && this.tackling <= 0 && this.stun <= 0 && this.state == null) {
				this.velocity = this.velocity.lerp(dirTarget, 0.1f);
			}
			else {
				float curlDir = myMod(ball.angle - dirTarget.angle(), 360) - 180;
				if (curlDir > 5) {
					ball.curl -= this.curlSpeed * this.kicking * deltaTime;
				}
				else if (curlDir < -5) {
					ball.curl += this.curlSpeed * this.kicking * deltaTime;
				}
			}
		}
	}
	
	public class ComPlayer extends Player{

		PlayerState state;
		Vector2 posTarget;
		float thinkTime = 0.25f;
		float controlDelay = 0;
		float thinkCounter = (float) Math.random() * 0.25f;
		
		public ComPlayer(Vector2 position, int team, String name, Vector2 formation) {
			this.position = position;
			this.bounds.height = SIZE;
			this.bounds.width = SIZE;
			this.velocity = new Vector2();
			this.angle = this.velocity.angle();
			this.team = team;
			this.name = name;
			this.state = PlayerState.chasing;
			this.posTarget = new Vector2();
			this.formation = formation;
			this.myTeamPolar = new Polar[teamSizes[this.team]];
			this.theirTeamPolar = new Polar[teamSizes[1 - this.team]];
		}		

		public void recalcPlayer(float deltaTime) {
			if(deltaTime == 0) return;
			this.stateTime += deltaTime * this.velocity.len();
			this.controlDelay = Math.max(0, this.controlDelay - deltaTime);
			this.myTeam = this.getPlayerList(this.team);
			this.theirTeam = this.getPlayerList(1 - this.team);
			for (Player p: this.myTeam) {
				this.myTeamPolar[this.myTeam.indexOf(p)] = new Polar(p.position.cpy().sub(this.position).angle(), p.position.dst(this.position));
			}
			for (Player p: this.theirTeam) {
				this.theirTeamPolar[this.theirTeam.indexOf(p)] = new Polar(p.position.cpy().sub(this.position).angle(), p.position.dst(this.position));
			}
			if (this instanceof Goalkeeper && (this.state == PlayerState.closingangle)) this.angle = new Vector2(ball.position.x, ball.position.y).sub(this.position).angle();
			else if (this.velocity.len() > 0.1 && this.state != PlayerState.setpiecetaking && this.state != PlayerState.diving) this.angle = this.velocity.angle();
			if (this.velocity.len() < 0.1) {
				this.velocity = new Vector2();
			}
		}
		
		public void updatePlayer(float deltaTime) {
			if (!this.possession) this.runSpeed = 1;
			if(deltaTime == 0) return;
			Vector2 ballPos = new Vector2(ball.position.x, ball.position.y);
			float ballRel = ballPos.cpy().sub(this.position).dot(new Vector2(1,0).rotate(this.angle));
			Vector2 goalTarget;
			if (this instanceof User && ((User)this).dribbleTarget != null) goalTarget = ((User)this).dribbleTarget.cpy(); 
			else goalTarget = new Vector2(0, -1008 + (this.team * 2016));
			Vector2 myGoal = new Vector2(0, 1008 - (this.team * 2016));
			Vector2 offset = new Vector2(ball.position.x, ball.position.y).sub(goalTarget).nor().scl(10);
			Vector2 dirTarget = new Vector2(ball.position.x, ball.position.y).add(offset);
			// ACTIONS TO TAKE EVERY FRAME
			if (this.state != null) switch(this.state) {
			case chasing:
				if (Math.abs(dirTarget.cpy().sub(this.position).angle() - this.angle) < 20 && new Vector2(ball.position.x, ball.position.y).dst(this.position) < this.SIZE &&
						this.theirTeam.size() > 0 && this.theirTeam.get(0).position.dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE * 1.3) && !setPiece) {
					boolean reject = false;
					for (Player p: theirTeam) {
						if (between(this, ball, p, 60) && p.position.dst(this.position) < 75) reject = true;
					}
					if (!reject  && this.controlDelay <= 0) {
						this.tackling = 0.6f;
						this.velocity.nor().scl(this.dirSpeed * 1.5f);
					}
				}
				if (new Vector2(this.position.x, this.position.y).dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.55
						&& this.kicking <= 0 && ball.position.z < 50  && !setPiece) {
					if (this.tackling <= 0 && (ball.possession != (1-this.team)) &&
							this.velocity.cpy().sub(ball.velocity.x, ball.velocity.y).len() < 500) {
						clearPossession();
						this.possession = true;
						this.state = PlayerState.dribbling;
						this.runSpeed = DRIB_PEN;
						this.posTarget = goalTarget.rotate((float) Math.random()*40-20);
						this.runon = 0;
						passedTo = null;
						ball.possession = this.team;
						posPlayer = this;
						ball.curl = 0;
						ball.rotation = 0;
						ball.velocity = new Vector3();
						lastTouch = this.team;
						this.controlDelay = CONTROL_DELAY;
					}
					else if (this.tackling > 0) {
						ball.velocity.lerp(new Vector3(this.velocity.x, this.velocity.y, 2).scl(1.5f), 0.2f);
						if (posPlayer != null) {
							posPlayer.stun = 0.8f;
						}
						lastTouch = this.team;
					}
				}
				break;
			case dribbling:
				// LOSE CONTROL
				float ballAng = ballPos.cpy().sub(this.position).angle();
				float targAng = this.posTarget.cpy().sub(this.position).angle();
				// Facilitate minor changes of direction
				if (angCompare(ballAng, targAng) < 30 && angCompare (ballAng, targAng) > 2) {
					float changeAng = ballAng + (90 * Math.signum(targAng - ballAng));
					ball.position.add(new Vector3(deltaTime * 50, 0, 0).rotate(changeAng, 0, 0, 1));
//					System.out.println("NUDGE...");
				}
				if (ballRel < 0) ball.position.add(new Vector3(deltaTime * 50, 0, 0).rotate(this.angle, 0, 0, 1));
				if (this.position.dst(ball.position.x, ball.position.y) > (this.SIZE + Ball.SIZE) * 0.8) {
					this.runSpeed = 1;
					this.possession = false;
					ball.possession = -1;
					posPlayer = null;
					this.state = PlayerState.chasing;
				}
				// PUSH BALL AHEAD
//				else if (this.position.dst(ball.position.x, ball.position.y) < this.SIZE * CONTROL * 0.75) {
//					Vector2 bestBallDir = this.position.cpy().add(new Vector2(this.posTarget.cpy().sub(this.position).nor().scl(SIZE + CONTROL)))
//							.sub(new Vector2(ball.position.x, ball.position.y)).scl(1).limit(this.dirSpeed * this.runSpeed * 1.5f);
//					ball.velocity.add(new Vector3(bestBallDir.x, bestBallDir.y, -ball.velocity.z * 0.5f));
//				}
				if (this.position.dst(this.posTarget) < 1) this.posTarget = this.position;
				break;
			case moving:
				if (this.position.dst(this.posTarget) < 10) {
					this.posTarget = this.position;
					this.velocity.scl(0.95f);
				}
				break;
			case positioning:
				break;
			case marking:
				break;
			case stunned:
				break;
			case runningon:
				if (this.position.dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.80
						&& ball.position.z < 50) {
					if (this.tackling <= 0 && (ball.possession != (1-this.team)) &&
							this.velocity.cpy().sub(ball.velocity.x, ball.velocity.y).len() < 500) {
						clearPossession();
						this.possession = true;
						Vector2 controlVector = new Vector2(this.position.x - ball.position.x, this.position.y - ball.position.y).scl(0.5f).
								add(this.velocity.cpy().scl(0.8f));
						ball.velocity = new Vector3(controlVector.x, controlVector.y, 0);
						this.state = PlayerState.dribbling;
						this.runSpeed = DRIB_PEN;
						this.posTarget = goalTarget.rotate((float) Math.random()*40-20);
						this.runon = 0;
						this.kicking = 0.1f;
						ball.possession = this.team;
						posPlayer = this;
						ball.curl = 0;
						ball.rotation = 0;
						lastTouch = this.team;
						this.controlDelay = CONTROL_DELAY;
					}
				}
				else if (this.runon >= 0) {
					this.velocity = passEnd.cpy().sub(this.position).sub(this.position.cpy().sub(goalTarget).limit(10)).scl(60).limit(this.dirSpeed * this.runSpeed); // .sub(this.position.cpy().sub(goalTarget).limit(10))
				}
				break;
			case waiting:
				this.posTarget = this.position.cpy().add(this.velocity);
				this.velocity.scl(0.94f);
				break;
			case setpiecetaking:
				this.velocity = new Vector2();
				break;
			case toball:
				break;
			case closingangle:
				this.angle = ballPos.cpy().sub(this.position).angle();
				if (new Vector2(this.position.x, this.position.y).dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.55
						&& this.kicking <= 0 && ball.position.z < 50  && !setPiece) {
					if (this.velocity.cpy().sub(ball.velocity.x, ball.velocity.y).len() < controlSpeed) {
						clearPossession();
						this.possession = true;
						this.state = PlayerState.dribbling;
						this.runSpeed = DRIB_PEN;
						this.posTarget = goalTarget.rotate((float) Math.random()*40-20);
						this.runon = 0;
						passedTo = null;
						ball.possession = this.team;
						posPlayer = this;
						ball.curl = 0;
						ball.rotation = 0;
						ball.velocity = new Vector3();
						lastTouch = this.team;
						this.controlDelay = CONTROL_DELAY;
					}
				}
				break;
			case diving:
				if (new Vector2(this.position.x, this.position.y).dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.55
						&& this.kicking <= 0 && ball.position.z < 50  && !setPiece) {
					if (this.velocity.cpy().sub(ball.velocity.x, ball.velocity.y).len() < controlSpeed) {
						clearPossession();
						this.possession = true;
						this.state = PlayerState.dribbling;
						this.runSpeed = DRIB_PEN;
						this.posTarget = goalTarget.rotate((float) Math.random()*40-20);
						this.runon = 0;
						passedTo = null;
						ball.possession = this.team;
						posPlayer = this;
						ball.curl = 0;
						ball.rotation = 0;
						ball.velocity = new Vector3();
						lastTouch = this.team;
						this.controlDelay = CONTROL_DELAY;
					}
				}
				break;
			case holding:
				break;
			}
			if (offPitch(posTarget) && (!setPiece)) posTarget = ballPos.cpy().add(ballPos.cpy().sub(posTarget));
			// AVOID TEAMMATES
			if (this.state != PlayerState.runningon && this.state != PlayerState.dribbling && this.state != PlayerState.setpiecetaking
					&& !ballPlayers.get(this.team).get(0).equals(this)) {
				for (Player m: myTeam) {
					if (m.position.dst(this.position) < 150) {
						this.velocity.add(this.position.cpy().sub(m.position).nor().scl(100 * (float) Math.exp(-Math.max(m.position.dst(this.position)/150, 0.9f))));
//						System.out.println("EJECTED WITH IMPULSE: " + 100 * (float) Math.exp(-Math.max(m.position.dst(this.position)/150, 0.9f)));
//						else this.posTarget.add(this.position.cpy().sub(m.position).nor().scl(5));
					}
				}
			}
			// ACTIONS TO TAKE EVERY TIME THE COMPUTER PLAYER REASSESSES
			Vector2 midGoal;
			if (this instanceof User && ((User)this).dribbleTarget != null) midGoal = ((User)this).dribbleTarget;
			else midGoal = new Vector2(0, -1008 + (2016 * this.team));
			float midGoalAngle = midGoal.cpy().sub(this.position).angle();
			this.thinkCounter -= deltaTime;
			if (this.thinkCounter <= 0) {
				this.thinkCounter = this.thinkTime;
				if (this instanceof User && ((User)this).dribbleTarget != null && ((User)this).dribbleTarget.dst(this.position) < this.SIZE) ((User)this).dribbleTarget = null;
				switch(this.state) {
				case dribbling:
					// Calculate dribbling scores
					this.posTarget = goalTarget.rotate((float) Math.random()*20-10);
					ArrayList<DirScore> directions = new ArrayList<DirScore>();
					for (float ang = (float) Math.random() * 10f; ang < 360; ang += 10) {
						float basescore = 0;
						for (Polar p: theirTeamPolar) {
							float angDiff = angCompare(p.angle, ang);
							if (p.distance < 200) {
								if (angDiff < 75) basescore -= (75 - angDiff) * Math.exp(-p.distance / 200) * 3;
								if (angDiff > 75 & angDiff < 105) basescore += 15 * Math.exp(-p.distance / 200) * 2;
							}
						}
						basescore += (90 - angCompare(ang, new Vector2(ball.position.x, ball.position.y).sub(this.position).angle())) / (Math.exp(Math.max(-this.position.dst(ball.position.x, ball.position.y), -30) / 180));
						basescore += (90 - angCompare(ang, midGoalAngle)) / 2;
						if (offPitch(this.position.cpy().add(new Vector2(90,0).rotate(ang)))) basescore -= 100;
						if (Math.abs(this.position.y) > 500 && angCompare(ang, goalTarget.cpy().sub(this.position).angle()) < 20) basescore += 50;
						directions.add(new DirScore(ang, basescore));
					}
					Collections.sort(directions, directions.get(0));

					// Calculate passing scores
					float bestScore = 0;
					Vector2 bestVector = new Vector2(250,0).rotate(dirTarget.angle());
					Player passTarget = null;
					float passTime = 0;
					float ang = this.angle - 75 + (float) (5 * Math.random());
					Vector2 passRequired = new Vector2();
					passEnd = bestVector.cpy();
					String bestDetails = "";
					while (ang <= this.angle + 75 && ballRel > 0) {
						for (Player p: this.myTeam) {
							String details = "";
							Vector2 intersect = new Vector2();
							Intersector.intersectLines(ballPos, ballPos.cpy().add(new Vector2(1, 0).rotate(ang)), p.position, p.position.cpy().add(p.velocity), intersect);
							passRequired = new Vector2(intersect.x - ball.position.x, intersect.y - ball.position.y).add(p.velocity.cpy().limit(p.SIZE/2));
							float time = (intersect.x - p.position.x) / p.velocity.x;
							float denom = 1 - (float) Math.exp(time * -GROUND_RESISTANCE);
							Vector2 passVector = passRequired.scl(GROUND_RESISTANCE/denom);
							if (time < 0.2f && time > 10 || passRequired.len() > 1000 || passRequired.len()/time < 100 ||
									passVector.len() * Math.exp(-GROUND_RESISTANCE * time) > 500  || angCompare(passRequired.angle(), ang) > 10) {
								details += "Ang: " + ang + " to " + p.name + ", rejected; pass angle" + passRequired.angle() + ", time: " + time + ", pass length:" + passRequired.len() + ", ratio: " + passRequired.len()/time + ", impulse: " + passVector.len() * Math.exp(-GROUND_RESISTANCE * time);
								if (PASSPAUSE) System.out.println(details);
								continue;
							}
							float newScore = (float) Math.exp(-Math.abs(800 - passVector.len())/500);
							details += "Ang: " + ang + " to " + p.name + ", base sc: " + newScore + " (" + passVector.len() + ")";
							for (Player q: this.theirTeam) {
								if (between(this, intersect, q, 10)) {
									newScore -= 1;
									details += " Int Pl: " + q.name + "=-1";
								}
								newScore -= Math.exp(-intersect.dst(q.position)/(q.dirSpeed*time));
								if (Math.exp(-intersect.dst(q.position)/(time*q.dirSpeed)) > 0.01f) details += " Prox pen: " + q.name + " " + Math.exp(-intersect.dst(q.position)/(time*q.dirSpeed));
								newScore += Math.exp(-q.position.dst(ballPos)/100);
								if (Math.exp(-q.position.dst(ballPos)/100) > 0.01f) details += " Press bon: " + q.name + " " + Math.exp(-q.position.dst(ballPos)/100);
							}
							if (Math.abs(intersect.x) > 640 || Math.abs(intersect.y) > 1024) {
								newScore -= 1;
								details += " Off pitch: -1";
							}
							if (p.equals(this.calledFor)) {
								newScore += 0.5;
								details += " Called for: +0.5";
							}
							float pSpotAng = 270 - (180 * this.team);
							float angImprove = angCompare(midGoalAngle, pSpotAng) - angCompare(midGoal.cpy().sub(p.position).angle(), pSpotAng);
							if (angImprove > 8) {
								newScore += angImprove/100;
							}
							if (PASSPAUSE) System.out.println(details + " Net: "+ newScore);
							if (newScore > bestScore) {
								bestScore = newScore;
								bestVector = passVector.cpy();
								passTarget = p;
								passTime = time;
								passEnd = intersect;
								bestDetails = details;
							}
						}
						ang += 2.5;
					}
					bestScore *= (210 - angCompare(passEnd.cpy().sub(this.position).angle(), goalTarget.cpy().sub(this.position).angle()))/1.4f;
					if (PASSPAUSE) {
						System.out.println(" Net best: " + bestScore);
						PASSPAUSE = false;
						PAUSED = true;
					}
					
					// Calculate Shooting scores
					float bestShoot = 0;
					float shootScore = 0;
					Vector3 shootVect = new Vector3(0, 0, 0);
					float shootCurl = 0;
					if (ballRel > 0) {
						for (int i=(3*(1-this.team)); i<3*(2-this.team); i++) {
							if (angCompare(this.angle, corners[i].cpy().sub(ballPos).angle()) < 45) {
								shootScore = ((float) Math.random() * 40) - 30 + (float) Math.exp(-corners[i].dst(ballPos) / 800) * 250;
								shootScore -= Math.pow((corners[i].cpy().sub(ballPos).angle() % 180 - 90)/5, 2);
								for (Player p: theirTeam) {
									if (between(ballPos, corners[i], p, 10)) shootScore -= 50;
									if (p instanceof Goalkeeper && between(ballPos, corners[i], p, 7)) shootScore -=25;
								}
								float midGoalDist = this.position.dst(midGoal);
								for (Player p: myTeam) {
									if (midGoalDist > p.position.dst(midGoal) + 50) shootScore -= 20;
								}
								if (midGoalDist < 150) shootScore += 80;
								if (midGoalDist < 75) shootScore += 80;
								if (corners[i].x == 0) shootScore += 50;
							}
							if (shootScore >= bestShoot - 15 + Math.random() * 30) {
								if (bestShoot == 0 || Math.random() < 0.3) {
									bestShoot = shootScore;
									Vector2 cornerDir = corners[i].cpy().sub(ballPos).nor().scl(Math.max(400 + corners[i].dst(ballPos)/2, 700));
									cornerDir.rotate(8 - ((float) Math.random() * 16));
									shootCurl = ((float) Math.random() * 50) - 25;
									cornerDir.rotate(-shootCurl * cornerDir.len() / 2000);
									shootVect = new Vector3(cornerDir.x, cornerDir.y, (float) Math.pow(cornerDir.len(), 1.9)/(1200 + (float)Math.random() * 800));
								}
							}
						}
					}

					// Decide what to do based on scores

					if (directions.get(0).score > 50 && directions.get(0).score > bestScore  && directions.get(0).score > bestShoot) {
						System.out.println("Dribbling" + goalTarget + ": " + directions.get(0).score + ", Passing: " + bestScore + ", Shooting : " + bestShoot);
						this.posTarget = this.position.cpy().add(new Vector2(directions.get(0).score - 50, 0).rotate(directions.get(0).angle));
						lastTouch = this.team;
					}
					else if (bestScore > 50 && bestScore > bestShoot && this.controlDelay <= 0) {
						System.out.println("Passing: " + bestScore + ", Dribbling: " + directions.get(0).score + ", Shooting: " + bestShoot);
						System.out.println(bestDetails);
						ball.velocity = new Vector3(bestVector.x, bestVector.y, 0);
						ball.position.z = 0;
						this.possession = false;
						if (passTarget != null) {
							passTarget.runon = passTime + 0.5f;
						}
						posPlayer = null;
						this.kicking = 0.5f;
						lastTouch = this.team;
					}
					else if (bestShoot > 50 && this.controlDelay <= 0) {
						System.out.println("Shooting: " + bestShoot + ", Passing: " + bestScore + ", Dribbling: " + directions.get(0).score);
						this.kicking = 0.8f;
						this.possession = false;
						posPlayer = null;
						ball.possession = -1;
						ball.velocity = shootVect;
						ball.curl = shootCurl;
						lastTouch = this.team;
					}
					else if (angCompare(this.angle, midGoalAngle) < 120 && this.position.y * (1 - (2 * this.team)) > 200 &&
							ballRel > 0) {
						Vector2 clearDir = new Vector2(Math.max(midGoal.dst(ballPos)/3, 200), 0);
						clearDir.rotate(12 - ((float) Math.random() * 24));
						if (angCompare(this.angle, midGoalAngle) > 30) {
							clearDir.rotate(midAngle(this.angle, midGoalAngle));
						}
						else clearDir.rotate(midGoalAngle);
						ball.velocity = new Vector3(clearDir.x, clearDir.y, (float) Math.pow(clearDir.len(), 1.8)/(300 + (float)Math.random() * 200));
						this.kicking = 0.8f;
						this.possession = false;
						posPlayer = null;
						ball.possession = -1;
						ball.curl = ((float) Math.random() * 50) - 25;
						System.out.println("HOOF!!! " + "to goal: " + midGoalAngle + ", myDir: " + this.angle + ", result: " + midAngle(this.angle, midGoalAngle) + ", Impulse: " + ball.velocity.len());
						lastTouch = this.team;
					}
					else {
						this.posTarget = this.position.cpy();
						this.velocity.scl(0.5f);
					}
					break;
				case moving:
					if (this instanceof User && ((User)this).inputTime < 5) {}
					else if (posPlayer != null) {
						float dir = ((myMod(posPlayer.angle + 270 - (180 * team), 360) - 180) / 2) - 90 + (180 * team);
						if (myMod(dir, 180) > 90) dir += Math.max(0, (Math.abs(posPlayer.position.y)- 750) / 2.5);
						else dir -= Math.max(0, (Math.abs(posPlayer.position.y)- 750) / 2.5);
						Vector2 newTarget;
						if (!setPiece) newTarget = posPlayer.position.cpy().add(new Vector2(400,0).rotate(dir + ((float) Math.random() * 15)));
						else newTarget = posPlayer.position.cpy().sub(posPlayer.position.cpy().nor().scl(320));
						if (Math.abs(newTarget.x) < 150 && Math.abs(newTarget.y) > 800) newTarget = new Vector2(newTarget.x, 900 * Math.signum(newTarget.y));
						if (Math.abs(newTarget.x) > 624) newTarget = new Vector2(Math.signum(newTarget.x) * (1248 - Math.abs(newTarget.x)), newTarget.y);
						if (Math.abs(newTarget.y) > 1008) newTarget = new Vector2(Math.signum(newTarget.y) * (1248 - Math.abs(newTarget.y)), newTarget.x);
						this.posTarget = newTarget;
					}
					else if (setPiece) this.posTarget = ballPos.cpy().sub(ballPos.cpy().nor().scl(200 + (((int)masterTime%3)*50)).rotate(25 - ((int)masterTime%10)*5));
					break;
				case marking:
					break;
				case positioning:
					this.posTarget = this.formation.cpy();
					break;
				case chasing:
					if (posPlayer != null) {
						this.posTarget = dirTarget;
						float ang1 = new Vector2(ball.position.x, ball.position.y).sub(posPlayer.position).angle();
						float ang2 = new Vector2(ball.position.x, ball.position.y).sub(this.position).angle();
						if (angCompare(ang1, ang2) < 90) {
							if (((ang1 - ang2 + 180) % 360) - 180 > 0) {
								this.posTarget = dirTarget.cpy().add(new Vector2(ball.position.x, ball.position.y).sub(posPlayer.position).nor().scl(this.SIZE * 2).rotate((((ang1 - ang2 + 180) % 360) - 180) * 2 - 180));
							}
							else {
								this.posTarget = dirTarget.cpy().add(new Vector2(ball.position.x, ball.position.y).sub(posPlayer.position).nor().scl(this.SIZE * 2).rotate(-(((ang1 - ang2 + 180) % 360) - 180) * 2 - 180));
							}
						}
					}					
					else {
						Vector2 ballOffset = new Vector2(ball.position.x - this.position.x, ball.position.y - this.position.y);
						Vector2 ball2dVel = new Vector2(ball.velocity.x, ball.velocity.y);
						float dotP = ballOffset.dot(ball2dVel);
						float rho;
						if (ball.position.z < 5) rho = GROUND_RESISTANCE;
						else rho = AIR_RESISTANCE;
						float eqB = 2 * dotP;
						float eqA = (float)Math.pow(this.dirSpeed, 2)  - ball2dVel.len2() - (2 * rho * dotP) ;
						float eqC = -ballOffset.len2();
						if (Math.pow(eqB,  2) > (4 * eqA * eqC)) {
							double rootTerm = Math.sqrt(Math.pow(eqB,  2) - (4 * eqA * eqC));
							float T = (float) (-eqB-rootTerm)/(2*eqA);
							if (T > 0) {
								Vector3 ballTarget = ball.position.cpy().add(ball.velocity.cpy().scl((1 - (float)Math.exp(-GROUND_RESISTANCE * T))/GROUND_RESISTANCE));
								this.posTarget = new Vector2(ballTarget.x, ballTarget.y);
							}
							else this.posTarget = dirTarget;
						}
						else this.posTarget = dirTarget;
//						System.out.println("Calculated times are: " + (-eqB+rootTerm)/(2*eqA) + " and " + (-eqB-rootTerm)/(2*eqA));
					}
					if (setPiece) this.posTarget.add(new Vector2(0, 1008 - (this.team * 2016)).sub(ballPos).nor().scl(200));
					break;
				case stunned:
					break;
				case runningon:
					break;
				case waiting:
					break;
				case setpiecetaking:
					this.velocity = new Vector2();
					if (Math.random() < 0.25 && setPiecePatience < 46) {
						// Calculate passing scores
						bestScore = 0;
						bestVector = new Vector2(250,0).rotate(dirTarget.angle());
						passTarget = null;
						passTime = 0;
						ang = this.angle - 90 + (float) (5 * Math.random());
						passRequired = new Vector2();
						passEnd = bestVector.cpy();
						bestDetails = "";
						while (ang <= this.angle + 90) {
							for (Player p: this.myTeam) {
								String details = "";
								Vector2 intersect = new Vector2();
								Intersector.intersectLines(ballPos, ballPos.cpy().add(new Vector2(1, 0).rotate(ang)), p.position, p.position.cpy().add(p.velocity), intersect);
								passRequired = new Vector2(intersect.x - ball.position.x, intersect.y - ball.position.y);
								float time = (intersect.x - p.position.x) / p.velocity.x;
								float denom = 1 - (float) Math.exp(time * -GROUND_RESISTANCE);
								Vector2 passVector = passRequired.scl(GROUND_RESISTANCE/denom);
								if (time < 0.2f && time > 10 || passRequired.len() > 1000 || passRequired.len()/time < 100 ||
										passVector.len() * Math.exp(-GROUND_RESISTANCE * time) > 500  || angCompare(passRequired.angle(), ang) > 10) {
									details += "Ang: " + ang + " to " + p.name + ", rejected; pass angle" + passRequired.angle() + ", time: " + time + ", pass length:" + passRequired.len() + ", ratio: " + passRequired.len()/time + ", impulse: " + passVector.len() * Math.exp(-GROUND_RESISTANCE * time);
									if (PASSPAUSE) System.out.println(details);
									continue;
								}
								float newScore = (float) Math.exp(-Math.abs(800 - passVector.len())/500);
								details += "Ang: " + ang + " to " + p.name + ", base sc: " + newScore + " (" + passVector.len() + ")";
								for (Player q: this.theirTeam) {
									if (between(this, intersect, q, 10)) {
										newScore -= 1;
										details += " Int Pl: " + q.name + "=-1";
									}
									newScore -= Math.exp(-intersect.dst(q.position)/(q.dirSpeed*time));
									if (Math.exp(-intersect.dst(q.position)/(time*q.dirSpeed)) > 0.01f) details += " Prox pen: " + q.name + " " + Math.exp(-intersect.dst(q.position)/(time*q.dirSpeed));
									newScore += Math.exp(-q.position.dst(ballPos)/100);
									if (Math.exp(-q.position.dst(ballPos)/100) > 0.01f) details += " Press bon: " + q.name + " " + Math.exp(-q.position.dst(ballPos)/100);
								}
								if (Math.abs(intersect.x) > 640 || Math.abs(intersect.y) > 1024) {
									newScore -= 1;
									details += " Off pitch: -1";
								}
								if (p.equals(this.calledFor)) {
									newScore += 0.5;
									details += " Called for: +0.5";
								}
								if (PASSPAUSE) System.out.println(details + " Net: "+ newScore);
								if (newScore > bestScore) {
									bestScore = newScore;
									bestVector = passVector.cpy();
									passTarget = p;
									passTime = time;
									passEnd = intersect;
									bestDetails = details;
								}
							}
							ang += 2.5;
						}
						bestScore *= (210 - angCompare(passEnd.cpy().sub(this.position).angle(), goalTarget.cpy().sub(this.position).angle()))/1.4f;
						if (PASSPAUSE) {
							System.out.println(" Net best: " + bestScore);
							PASSPAUSE = false;
							PAUSED = true;
						}
						
						// Calculate Shooting scores
						bestShoot = 0;
						shootScore = 0;
						shootVect = new Vector3(0, 0, 0);
						shootCurl = 0;
						midGoal = new Vector2(0, -1008 + (2016 * this.team));
						for (int i=(3*(1-this.team)); i<3*(2-this.team); i++) {
							if (angCompare(this.angle, corners[i].cpy().sub(ballPos).angle()) < 45) {
								shootScore = ((float) Math.random() * 40) - 30 + (float) Math.exp(-corners[i].dst(ballPos) / 800) * 250;
								shootScore -= Math.pow((corners[i].cpy().sub(ballPos).angle() % 180 - 90)/5, 2);
								for (Player p: theirTeam) {
									if (between(ballPos, corners[i], p, 10)) shootScore -= 50;
								}
								float midGoalDist = this.position.dst(midGoal);
								for (Player p: myTeam) {
									if (midGoalDist > p.position.dst(midGoal) + 50) shootScore -= 20;
								}
								if (corners[i].x == 0) shootScore += 50;
							}
							if (shootScore >= bestShoot + Math.random() * 50 - 25) {
								bestShoot = shootScore;
								Vector2 cornerDir = corners[i].cpy().sub(ballPos).nor().scl(Math.max(400 + corners[i].dst(ballPos)/2, 700));
								cornerDir.rotate(8 - ((float) Math.random() * 16));
								shootCurl = ((float) Math.random() * 50) - 25;
								cornerDir.rotate(-shootCurl * cornerDir.len() / 2000);
								shootVect = new Vector3(cornerDir.x, cornerDir.y, (float) Math.pow(cornerDir.len(), 1.9)/(1200 + (float)Math.random() * 800));
							}
						}
						
						// Decide what to do based on scores
						midGoalAngle = midGoal.cpy().sub(this.position).angle();
						if (bestScore > setPiecePatience && bestScore > bestShoot) {
							System.out.println("Passing: " + bestScore + ", Shooting: " + bestShoot);
							System.out.println(bestDetails);
							ball.velocity = new Vector3(bestVector.x, bestVector.y, 0);
							this.possession = false;
							if (passTarget != null) {
								passTarget.runon = passTime + 0.5f;
							}
							posPlayer = null;
							this.kicking = 0.5f;
							setPiece = false;
							setPieceTeam = -1;
							this.state = PlayerState.moving;
							lastTouch = this.team;
						}
						else if (bestShoot > setPiecePatience) {
							System.out.println("Shooting: " + bestShoot + ", Passing: " + bestScore);
							this.kicking = 0.8f;
							this.possession = false;
							posPlayer = null;
							ball.possession = -1;
							ball.velocity = shootVect;
							ball.curl = shootCurl;
							setPiece = false;
							setPieceTeam = -1;
							this.state = PlayerState.moving;
							lastTouch = this.team;
						}
						else if ((angCompare(this.angle, midGoalAngle) < 90 && this.position.y * (1 - (2 * this.team)) > 200) || setPiecePatience <= 0) {
							Vector2 clearDir = new Vector2(Math.max(midGoal.dst(ballPos)/3, 200), 0);
							clearDir.rotate(12 - ((float) Math.random() * 24));
							if (angCompare(this.angle, midGoalAngle) > 30) {
								clearDir.rotate(midAngle(this.angle, midGoalAngle));
							}
							else clearDir.rotate(midGoalAngle);
							ball.velocity = new Vector3(clearDir.x, clearDir.y, (float) Math.pow(clearDir.len(), 1.8)/(300 + (float)Math.random() * 200));
							this.kicking = 0.8f;
							this.possession = false;
							posPlayer = null;
							ball.possession = -1;
							ball.curl = ((float) Math.random() * 50) - 25;
							System.out.println("HOOF!!! " + "to goal: " + midGoalAngle + ", myDir: " + this.angle + ", result: " + midAngle(this.angle, midGoalAngle));
							lastTouch = this.team;
							setPiece = false;
							setPieceTeam = -1;
							this.state = PlayerState.moving;
						}		
						// Crossing Term
						else if (setPiecePatience < 30 && ball.position.y * ((this.team * 2) - 1) > 350) {
							Vector2 clearDir = midGoal.cpy().scl(0.85f).sub(this.position).scl(0.9f).limit(550);
							clearDir.rotate(6 - ((float) Math.random() * 12));
							ball.velocity = new Vector3(clearDir.x, clearDir.y, (float) Math.pow(clearDir.len(), 1.8)/(300 + (float)Math.random() * 200));
							this.kicking = 0.8f;
							this.possession = false;
							posPlayer = null;
							ball.possession = -1;
							ball.curl = ((float) Math.random() * 50) - 25;
							System.out.println("CROSS!!! " + "to goal: " + midGoalAngle + ", myDir: " + this.angle + ", result: " + midAngle(this.angle, midGoalAngle) + ", Impulse: " + ball.velocity.len());
							lastTouch = this.team;
							setPiece = false;
							setPieceTeam = -1;
							this.state = PlayerState.moving;							
						}
					}
					break;
				case toball:
					break;
				case closingangle:
					float posDist = Math.max(Math.min(ballPos.dst(myGoal) - 120, 300) - Math.max((ballPos.dst(myGoal) - 300)/2, 0), 10);
					this.posTarget = myGoal.cpy().add(ballPos.cpy().sub(myGoal).nor().scl(posDist));
					break;
				case diving:
					break;
				case holding:
					break;
				}
			}
			// KEEP PLAYER IN BOUNDS AND CHANGE VELOCITY
			if (Math.abs(this.posTarget.x) > 672) this.posTarget.x = 672 * Math.signum(this.posTarget.x);
			if (Math.abs(this.posTarget.y) > 1056) this.posTarget.y = 1056 * Math.signum(this.posTarget.y);
			if (this.kicking <= 0 && this.tackling <= 0 && this.stun <= 0 && this.runon <= 0 && this.state != PlayerState.setpiecetaking) {
				if (this.position.dst(this.posTarget) > 10)	this.velocity = this.velocity.lerp(this.posTarget.cpy().sub(this.position).nor().scl(this.dirSpeed * this.runSpeed), 0.05f);
				}
			if (this.diving <= 0 && this.tackling <= 0) this.velocity.limit(this.dirSpeed * this.runSpeed);
			this.position.add(this.velocity.cpy().scl(deltaTime));
			if (this.kicking > 0 || this.tackling > 0) this.velocity.scl(1 - (deltaTime * TACKLE_SLOWDOWN));
			if (this.stun > 0) this.velocity.scl(0.9f);
			// impact on ball
			if (setPiece) {}
			else if (this.position.dst(ball.position.x, ball.position.y) < this.SIZE * CONTROL * 0.75 && ball.position.z < 100 && 
					this.velocity.cpy().add(new Vector2(ball.velocity.x, ball.velocity.y)).len() > this.controlSpeed && this.kicking <= 0) {	
				float impulse = this.velocity.cpy().add(new Vector2(ball.velocity.x, ball.velocity.y)).len();
				float restAngle = (2 * this.position.cpy().sub(ballPos).angle()) - new Vector2(ball.velocity.x, ball.velocity.y).angle() - 180;
				Vector3 axisVect = new Vector3(0, 0, 1).rotate((float)Math.random() * 30, (float)Math.random(), (float)Math.random(), 0);
				ball.velocity = new Vector3(impulse * RICOCHET_COEFFICIENT, 0, 0).rotate(axisVect, restAngle);
				lastTouch = this.team;
			}			
			else if (this.position.dst(ball.position.x, ball.position.y) < this.SIZE * CONTROL * 0.75 && this.stun <= 0 && this.kicking <= 0 && ball.position.z < 100) {
				Vector2 bestBallDir = this.position.cpy().add(new Vector2(this.posTarget.cpy().sub(this.position).nor().scl(SIZE + CONTROL)))
						.sub(new Vector2(ball.position.x, ball.position.y)).scl(1).limit(this.dirSpeed * this.runSpeed * 1.5f);
				ball.velocity.add(new Vector3(bestBallDir.x, bestBallDir.y, -ball.velocity.z * 0.5f));
				lastTouch = this.team;
			}
			this.kicking = Math.max(this.kicking - deltaTime, 0);
			this.tackling = Math.max(this.tackling - deltaTime, 0);
			if (this.runon > 0) {
				this.runon -= deltaTime;
				if (this.runon <= 0) {
					this.runon = 0;
					ball.possession = -1;
					passedTo = null;
				}
			}
			this.stun = Math.max(0, this.stun - deltaTime);
			this.label = this.name;// + ": " + this.state.toString();
		}
	}
	
	public class Goalkeeper extends ComPlayer {

		float diveLength;
		
		public Goalkeeper(Vector2 position, int team, String name, Vector2 formation) {
			super(position, team, name, formation);
			diveLength = 2.5f;
		}		

		public void updatePlayer(float deltaTime) {
			if (!this.possession) this.runSpeed = 1;
			if(deltaTime == 0) return;
			Vector2 ballPos = new Vector2(ball.position.x, ball.position.y);
			Vector2 goalTarget = new Vector2(0, -1008 + (this.team * 2016));
			Vector2 myGoal = new Vector2(0, 1008 - (this.team * 2016));
			Vector2 offset = new Vector2(ball.position.x, ball.position.y).sub(goalTarget).nor().scl(10);
			Vector2 dirTarget = new Vector2(ball.position.x, ball.position.y).add(offset);
			// ACTIONS TO TAKE EVERY FRAME
			switch(this.state) {
			case chasing:
				if (Math.abs(dirTarget.cpy().sub(this.position).angle() - this.angle) < 20 && new Vector2(ball.position.x, ball.position.y).dst(this.position) < this.SIZE &&
						this.theirTeam.size() > 0 && !setPiece) {
					boolean reject = false;
					for (Player p: theirTeam) {
						if (between(this, ball, p, 30) && p.position.dst(this.position) < 75) reject = true;
					}
					if (!reject  && this.controlDelay <= 0 && this.kicking <= 0) {
						this.tackling = 0.6f;
						this.velocity.nor().scl(this.dirSpeed * 1.5f);
					}
				}
				if (new Vector2(this.position.x, this.position.y).dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.55
						&& this.kicking <= 0 && ball.position.z < 50  && !setPiece) {
					if (this.tackling <= 0 && (ball.possession != (1-this.team)) &&
							this.velocity.cpy().sub(ball.velocity.x, ball.velocity.y).len() < 500) {
						clearPossession();
						this.possession = true;
						this.state = PlayerState.dribbling;
						this.runSpeed = DRIB_PEN;
						this.posTarget = goalTarget.rotate((float) Math.random()*40-20);
						this.runon = 0;
						passedTo = null;
						ball.possession = this.team;
						posPlayer = this;
						ball.curl = 0;
						ball.rotation = 0;
						ball.velocity = new Vector3();
						lastTouch = this.team;
						this.controlDelay = CONTROL_DELAY * 4;
					}
					else if (this.tackling > 0) {
						this.possession = true;
						this.state = PlayerState.holding;
						posPlayer = this;
						lastTouch = this.team;
					}
				}
				break;
			case dribbling:
				// LOSE CONTROL
				float ballAng = ballPos.cpy().sub(this.position).angle();
				float targAng = this.posTarget.cpy().sub(this.position).angle();
				// Facilitate minor changes of direction
				if (angCompare(ballAng, targAng) < 30 && angCompare (ballAng, targAng) > 2) {
					float changeAng = ballAng + (90 * Math.signum(targAng - ballAng));
					ball.position.add(new Vector3(deltaTime * 50, 0, 0).rotate(changeAng, 0, 0, 1));
//					System.out.println("NUDGE...");
				}
				if (this.position.dst(ball.position.x, ball.position.y) > (this.SIZE + Ball.SIZE) * 0.8) {
					this.runSpeed = 1;
					this.possession = false;
					ball.possession = -1;
					posPlayer = null;
					this.state = PlayerState.chasing;
				}
				ball.velocity = new Vector3();
				if (this.position.dst(this.posTarget) < 1) this.posTarget = this.position;
				break;
			case moving:
				break;
			case positioning:
				break;
			case marking:
				break;
			case stunned:
				break;
			case runningon:
				if (this.position.dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.80
						&& ball.position.z < 50) {
					if (this.tackling <= 0 && (ball.possession != (1-this.team)) &&
							this.velocity.cpy().sub(ball.velocity.x, ball.velocity.y).len() < 500) {
						clearPossession();
						this.possession = true;
						Vector2 controlVector = new Vector2(this.position.x - ball.position.x, this.position.y - ball.position.y).scl(0.5f).
								add(this.velocity.cpy().scl(0.8f));
						ball.velocity = new Vector3(controlVector.x, controlVector.y, 0);
						this.state = PlayerState.dribbling;
						this.runSpeed = DRIB_PEN;
						this.posTarget = goalTarget.rotate((float) Math.random()*40-20);
						this.runon = 0;
						this.kicking = 0.1f;
						ball.possession = this.team;
						posPlayer = this;
						ball.curl = 0;
						ball.rotation = 0;
						lastTouch = this.team;
						this.controlDelay = CONTROL_DELAY * 4;
					}
				}
				else if (this.runon >= 0) {
					this.velocity = passEnd.cpy().sub(this.position).sub(this.position.cpy().sub(goalTarget).limit(10)).scl(60).limit(this.dirSpeed * this.runSpeed); // .sub(this.position.cpy().sub(goalTarget).limit(10))
				}
				break;
			case waiting:
				this.posTarget = this.position.cpy().add(this.velocity);
				this.velocity.scl(0.94f);
				break;
			case setpiecetaking:
				break;
			case toball:
				break;
			case closingangle:
				this.angle = ballPos.cpy().sub(this.position).angle();
				if (new Vector2(this.position.x, this.position.y).dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.55
						&& this.kicking <= 0 && ball.position.z < 50  && !setPiece) {
					if (this.velocity.cpy().sub(ball.velocity.x, ball.velocity.y).len() < controlSpeed) {
						clearPossession();
						this.possession = true;
						this.state = PlayerState.dribbling;
						this.runSpeed = DRIB_PEN;
						this.posTarget = goalTarget.rotate((float) Math.random()*40-20);
						this.runon = 0;
						passedTo = null;
						ball.possession = this.team;
						posPlayer = this;
						ball.curl = 0;
						ball.rotation = 0;
						ball.velocity = new Vector3();
						lastTouch = this.team;
						this.controlDelay = CONTROL_DELAY * 4;
					}
				}
				break;
			case diving:
//				if (new Vector2(this.position.x, this.position.y).dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.55
//						&& this.kicking <= 0 && ball.position.z < 50  && !setPiece) {
//					if (this.velocity.cpy().sub(ball.velocity.x, ball.velocity.y).len() < controlSpeed) {
//						clearPossession();
//						this.possession = true;
//						this.state = PlayerState.dribbling;
//						this.runSpeed = DRIB_PEN;
//						this.posTarget = goalTarget.rotate((float) Math.random()*40-20);
//						this.runon = 0;
//						passedTo = null;
//						ball.possession = this.team;
//						posPlayer = this;
//						ball.curl = 0;
//						ball.rotation = 0;
//						ball.velocity = new Vector3();
//						lastTouch = this.team;
//						this.controlDelay = CONTROL_DELAY * 4;
//					}
//				}
				break;
			case holding:
				Vector2 inFront = this.position.cpy().add(new Vector2(this.SIZE/2, 0).rotate(this.angle));
				ball.position = new Vector3(inFront.x, inFront.y, 50);
				float goalDir = goalTarget.cpy().sub(this.position).angle();
				if (((goalDir - this.angle + 180) % 360) - 180 > 1) {
					this.angle += 1;
					this.velocity.rotate(1);
				}
				else if (((goalDir - this.angle + 180) % 360) - 180 > 1) {
					this.angle -= 1;
					this.velocity.rotate(-1);
				}
				this.posTarget = this.position.cpy().add(goalTarget.cpy().sub(this.position).nor().scl(10));
				ball.velocity = new Vector3();
				break;
			}
			if (offPitch(posTarget) && (!setPiece)) posTarget = ballPos.cpy().add(ballPos.cpy().sub(posTarget));
			// ACTIONS TO TAKE EVERY TIME THE COMPUTER PLAYER REASSESSES
			Vector2 midGoal = new Vector2(0, -1008 + (2016 * this.team));
			float midGoalAngle = midGoal.cpy().sub(this.position).angle();
			this.thinkCounter -= deltaTime;
			if (this.thinkCounter <= 0) {
				this.thinkCounter = this.thinkTime;
				switch(this.state) {
				case dribbling:
					// Calculate dribbling scores
					this.posTarget = goalTarget.rotate((float) Math.random()*20-10);
					ArrayList<DirScore> directions = new ArrayList<DirScore>();
					for (float ang = (float) Math.random() * 10f; ang < 360; ang += 10) {
						float basescore = 0;
						for (Polar p: theirTeamPolar) {
							float angDiff = angCompare(p.angle, ang);
							if (p.distance < 200) {
								if (angDiff < 75) basescore -= (75 - angDiff) * Math.exp(-p.distance / 200) * 3;
								if (angDiff > 75 & angDiff < 105) basescore += 15 * Math.exp(-p.distance / 200) * 2;
							}
						}
						basescore += (90 - angCompare(ang, new Vector2(ball.position.x, ball.position.y).sub(this.position).angle())) / (Math.exp(Math.max(-this.position.dst(ball.position.x, ball.position.y), -30) / 100));
						basescore += (90 - angCompare(ang, midGoalAngle)) / 2;
						basescore -= 50;
						if (angCompare(ang, midGoalAngle) < 10) basescore += 50;
						if (offPitch(this.position.cpy().add(new Vector2(90,0).rotate(ang)))) basescore -= 100;
						if (Math.abs(this.position.y) > 500 && angCompare(ang, goalTarget.cpy().sub(this.position).angle()) < 20) basescore += 50;
						directions.add(new DirScore(ang, basescore));
					}
					Collections.sort(directions, directions.get(0));

					// Calculate passing scores
					float bestScore = 0;
					Vector2 bestVector = new Vector2(250,0).rotate(dirTarget.angle());
					Player passTarget = null;
					float passTime = 0;
					float ang = this.angle - 75 + (float) (5 * Math.random());
					Vector2 passRequired = new Vector2();
					passEnd = bestVector.cpy();
					String bestDetails = "";
					while (ang <= this.angle + 75) {
						for (Player p: this.myTeam) {
							String details = "";
							Vector2 intersect = new Vector2();
							Intersector.intersectLines(ballPos, ballPos.cpy().add(new Vector2(1, 0).rotate(ang)), p.position, p.position.cpy().add(p.velocity), intersect);
							passRequired = new Vector2(intersect.x - ball.position.x, intersect.y - ball.position.y).add(p.velocity.cpy().limit(p.SIZE/2));
							float time = (intersect.x - p.position.x) / p.velocity.x;
							float denom = 1 - (float) Math.exp(time * -GROUND_RESISTANCE);
							Vector2 passVector = passRequired.scl(GROUND_RESISTANCE/denom);
							if (time < 0.2f && time > 10 || passRequired.len() > 1000 || passRequired.len()/time < 100 ||
									passVector.len() * Math.exp(-GROUND_RESISTANCE * time) > 500  || angCompare(passRequired.angle(), ang) > 10) {
								details += "Ang: " + ang + " to " + p.name + ", rejected; pass angle" + passRequired.angle() + ", time: " + time + ", pass length:" + passRequired.len() + ", ratio: " + passRequired.len()/time + ", impulse: " + passVector.len() * Math.exp(-GROUND_RESISTANCE * time);
								if (PASSPAUSE) System.out.println(details);
								continue;
							}
							float newScore = (float) Math.exp(-Math.abs(800 - passVector.len())/500);
							details += "Ang: " + ang + " to " + p.name + ", base sc: " + newScore + " (" + passVector.len() + ")";
							for (Player q: this.theirTeam) {
								if (between(this, intersect, q, 10)) {
									newScore -= 1;
									details += " Int Pl: " + q.name + "=-1";
								}
								newScore -= Math.exp(-intersect.dst(q.position)/(q.dirSpeed*time));
								if (Math.exp(-intersect.dst(q.position)/(time*q.dirSpeed)) > 0.01f) details += " Prox pen: " + q.name + " " + Math.exp(-intersect.dst(q.position)/(time*q.dirSpeed));
								newScore += Math.exp(-q.position.dst(ballPos)/100);
								if (Math.exp(-q.position.dst(ballPos)/100) > 0.01f) details += " Press bon: " + q.name + " " + Math.exp(-q.position.dst(ballPos)/100);
							}
							if (Math.abs(intersect.x) > 640 || Math.abs(intersect.y) > 1024) {
								newScore -= 1;
								details += " Off pitch: -1";
							}
							if (p.equals(this.calledFor)) {
								newScore += 0.5;
								details += " Called for: +0.5";
							}
							if (PASSPAUSE) System.out.println(details + " Net: "+ newScore);
							if (newScore > bestScore) {
								bestScore = newScore;
								bestVector = passVector.cpy();
								passTarget = p;
								passTime = time;
								passEnd = intersect;
								bestDetails = details;
							}
						}
						ang += 2.5;
					}
					bestScore *= (210 - angCompare(passEnd.cpy().sub(this.position).angle(), goalTarget.cpy().sub(this.position).angle()))/1.4f;
					if (PASSPAUSE) {
						System.out.println(" Net best: " + bestScore);
						PASSPAUSE = false;
						PAUSED = true;
					}
					
					// Calculate Shooting scores
					float bestShoot = 0;
					float shootScore = 0;
					Vector3 shootVect = new Vector3(0, 0, 0);
					float shootCurl = 0;
					for (int i=(3*(1-this.team)); i<3*(2-this.team); i++) {
						if (angCompare(this.angle, corners[i].cpy().sub(ballPos).angle()) < 45) {
							shootScore = ((float) Math.random() * 40) - 30 + (float) Math.exp(-corners[i].dst(ballPos) / 800) * 250;
							shootScore -= Math.pow((corners[i].cpy().sub(ballPos).angle() % 180 - 90)/5, 2);
							for (Player p: theirTeam) {
								if (between(ballPos, corners[i], p, 10)) shootScore -= 50;
							}
							float midGoalDist = this.position.dst(midGoal);
							for (Player p: myTeam) {
								if (midGoalDist > p.position.dst(midGoal) + 50) shootScore -= 20;
							}
							if (midGoalDist < 150) shootScore += 80;
							if (midGoalDist < 75) shootScore += 80;
							if (corners[i].x == 0) shootScore += 50;
						}
						if (shootScore >= bestShoot - 25 + (Math.random() * 50)) {
							if (bestShoot == 0 || Math.random() < 0.3) {
							bestShoot = shootScore;
							Vector2 cornerDir = corners[i].cpy().sub(ballPos).nor().scl(Math.max(400 + corners[i].dst(ballPos)/2, 700));
							cornerDir.rotate(8 - ((float) Math.random() * 16));
							shootCurl = ((float) Math.random() * 50) - 25;
							cornerDir.rotate(-shootCurl * cornerDir.len() / 2000);
							shootVect = new Vector3(cornerDir.x, cornerDir.y, (float) Math.pow(cornerDir.len(), 1.9)/(1600 + (float)Math.random() * 400));
							}
						}
					}
					
					// Decide what to do based on scores

					if (directions.get(0).score > 50 && directions.get(0).score > bestScore  && directions.get(0).score > bestShoot) {
						System.out.println("Dribbling" + goalTarget + ": " + directions.get(0).score + ", Passing: " + bestScore + ", Shooting : " + bestShoot);
						this.posTarget = this.position.cpy().add(new Vector2(directions.get(0).score - 50, 0).rotate(directions.get(0).angle));
						lastTouch = this.team;
					}
					else if (bestScore > 50 && bestScore > bestShoot && this.controlDelay <= 0) {
						System.out.println("Passing: " + bestScore + ", Dribbling: " + directions.get(0).score + ", Shooting: " + bestShoot);
						System.out.println(bestDetails);
						ball.velocity = new Vector3(bestVector.x, bestVector.y, 0);
						ball.position.z = 0;
						this.possession = false;
						if (passTarget != null) {
							passTarget.runon = passTime + 0.5f;
						}
						posPlayer = null;
						this.kicking = 0.5f;
						lastTouch = this.team;
					}
					else if (bestShoot > 50 && this.controlDelay <= 0) {
						System.out.println("Shooting: " + bestShoot + ", Passing: " + bestScore + ", Dribbling: " + directions.get(0).score);
						this.kicking = 0.8f;
						this.possession = false;
						posPlayer = null;
						ball.possession = -1;
						ball.velocity = shootVect;
						ball.curl = shootCurl;
						lastTouch = this.team;
					}
					else if (angCompare(this.angle, midGoalAngle) < 120 && this.position.y * (1 - (2 * this.team)) > 200 &&
							this.controlDelay <= 0) {
						Vector2 clearDir = new Vector2(Math.max(midGoal.dst(ballPos)/3, 200), 0);
						clearDir.rotate(12 - ((float) Math.random() * 24));
						if (angCompare(this.angle, midGoalAngle) > 30) {
							clearDir.rotate(midAngle(this.angle, midGoalAngle));
						}
						else clearDir.rotate(midGoalAngle);
						ball.velocity = new Vector3(clearDir.x, clearDir.y, (float) Math.pow(clearDir.len(), 1.8)/(300 + (float)Math.random() * 200));
						this.kicking = 0.8f;
						this.possession = false;
						posPlayer = null;
						ball.possession = -1;
						ball.curl = ((float) Math.random() * 50) - 25;
						System.out.println("HOOF!!! " + "to goal: " + midGoalAngle + ", myDir: " + this.angle + ", result: " + midAngle(this.angle, midGoalAngle) + ", Impulse: " + ball.velocity.len());
						lastTouch = this.team;
					}
					else {
						if (lastTouch != this.team) this.posTarget = new Vector2(ball.position.x, ball.position.y);
						else {
							this.posTarget = this.position.cpy();
							this.velocity.scl(0.5f);
						}
					}
					break;
				case moving:
					if (posPlayer != null) {
						float dir = ((myMod(posPlayer.angle + 270 - (180 * team), 360) - 180) / 2) - 90 + (180 * team);
						Vector2 newTarget;
						if (!setPiece) newTarget = posPlayer.position.cpy().add(new Vector2(400,0).rotate(dir + ((float) Math.random() * 15)));
						else newTarget = posPlayer.position.cpy().sub(posPlayer.position.cpy().nor().scl(320));
						this.posTarget = newTarget;
					}
					else if (setPiece) this.posTarget = ballPos.cpy().sub(ballPos.cpy().nor().scl(200 + (((int)masterTime%3)*50)).rotate(25 - ((int)masterTime%10)*5));
					break;
				case marking:
					break;
				case positioning:
					this.posTarget = this.formation.cpy();
					break;
				case chasing:
					Vector2 ballOffset = new Vector2(ball.position.x - this.position.x, ball.position.y - this.position.y);
					Vector2 ball2dVel = new Vector2(ball.velocity.x, ball.velocity.y);
					float dotP = ballOffset.dot(ball2dVel);
					float rho;
					if (ball.position.z < 5) rho = GROUND_RESISTANCE;
					else rho = AIR_RESISTANCE;
					float eqB = 2 * dotP;
					float eqA = (float)Math.pow(this.dirSpeed, 2)  - ball2dVel.len2() - (2 * rho * dotP) ;
					float eqC = -ballOffset.len2();
					if (Math.pow(eqB,  2) > (4 * eqA * eqC)) {
						double rootTerm = Math.sqrt(Math.pow(eqB,  2) - (4 * eqA * eqC));
						float T = (float) (-eqB-rootTerm)/(2*eqA);
						if (T > 0) {
							Vector3 ballTarget = ball.position.cpy().add(ball.velocity.cpy().scl((1 - (float)Math.exp(-GROUND_RESISTANCE * T))/GROUND_RESISTANCE));
							this.posTarget = new Vector2(ballTarget.x, ballTarget.y);
						}
						else this.posTarget = dirTarget;
					}
					else this.posTarget = dirTarget;
					//						System.out.println("Calculated times are: " + (-eqB+rootTerm)/(2*eqA) + " and " + (-eqB-rootTerm)/(2*eqA));
					if (setPiece) this.posTarget.add(new Vector2(0, 1008 - (this.team * 2016)).sub(ballPos).nor().scl(200));
					break;
				case stunned:
					break;
				case runningon:
					break;
				case waiting:
					break;
				case setpiecetaking:
					this.velocity = new Vector2();
					if (Math.random() < 0.25 && setPiecePatience < 46) {
						// Calculate passing scores
						bestScore = 0;
						bestVector = new Vector2(250,0).rotate(dirTarget.angle());
						passTarget = null;
						passTime = 0;
						ang = this.angle - 90 + (float) (5 * Math.random());
						passRequired = new Vector2();
						passEnd = bestVector.cpy();
						bestDetails = "";
						while (ang <= this.angle + 90) {
							for (Player p: this.myTeam) {
								String details = "";
								Vector2 intersect = new Vector2();
								Intersector.intersectLines(ballPos, ballPos.cpy().add(new Vector2(1, 0).rotate(ang)), p.position, p.position.cpy().add(p.velocity), intersect);
								passRequired = new Vector2(intersect.x - ball.position.x, intersect.y - ball.position.y);
								float time = (intersect.x - p.position.x) / p.velocity.x;
								float denom = 1 - (float) Math.exp(time * -GROUND_RESISTANCE);
								Vector2 passVector = passRequired.scl(GROUND_RESISTANCE/denom);
								if (time < 0.2f && time > 10 || passRequired.len() > 1000 || passRequired.len()/time < 100 ||
										passVector.len() * Math.exp(-GROUND_RESISTANCE * time) > 500  || angCompare(passRequired.angle(), ang) > 10) {
									details += "Ang: " + ang + " to " + p.name + ", rejected; pass angle" + passRequired.angle() + ", time: " + time + ", pass length:" + passRequired.len() + ", ratio: " + passRequired.len()/time + ", impulse: " + passVector.len() * Math.exp(-GROUND_RESISTANCE * time);
									if (PASSPAUSE) System.out.println(details);
									continue;
								}
								float newScore = (float) Math.exp(-Math.abs(800 - passVector.len())/500);
								details += "Ang: " + ang + " to " + p.name + ", base sc: " + newScore + " (" + passVector.len() + ")";
								for (Player q: this.theirTeam) {
									if (between(this, intersect, q, 10)) {
										newScore -= 1;
										details += " Int Pl: " + q.name + "=-1";
									}
									newScore -= Math.exp(-intersect.dst(q.position)/(q.dirSpeed*time));
									if (Math.exp(-intersect.dst(q.position)/(time*q.dirSpeed)) > 0.01f) details += " Prox pen: " + q.name + " " + Math.exp(-intersect.dst(q.position)/(time*q.dirSpeed));
									newScore += Math.exp(-q.position.dst(ballPos)/100);
									if (Math.exp(-q.position.dst(ballPos)/100) > 0.01f) details += " Press bon: " + q.name + " " + Math.exp(-q.position.dst(ballPos)/100);
								}
								if (Math.abs(intersect.x) > 640 || Math.abs(intersect.y) > 1024) {
									newScore -= 1;
									details += " Off pitch: -1";
								}
								if (p.equals(this.calledFor)) {
									newScore += 0.5;
									details += " Called for: +0.5";
								}
								if (PASSPAUSE) System.out.println(details + " Net: "+ newScore);
								if (newScore > bestScore) {
									bestScore = newScore;
									bestVector = passVector.cpy();
									passTarget = p;
									passTime = time;
									passEnd = intersect;
									bestDetails = details;
								}
							}
							ang += 2.5;
						}
						bestScore *= (210 - angCompare(passEnd.cpy().sub(this.position).angle(), goalTarget.cpy().sub(this.position).angle()))/1.4f;
						if (PASSPAUSE) {
							System.out.println(" Net best: " + bestScore);
							PASSPAUSE = false;
							PAUSED = true;
						}
						
						// Calculate Shooting scores
						bestShoot = 0;
						shootScore = 0;
						shootVect = new Vector3(0, 0, 0);
						shootCurl = 0;
						midGoal = new Vector2(0, -1008 + (2016 * this.team));
						for (int i=(3*(1-this.team)); i<3*(2-this.team); i++) {
							if (angCompare(this.angle, corners[i].cpy().sub(ballPos).angle()) < 45) {
								shootScore = ((float) Math.random() * 40) - 30 + (float) Math.exp(-corners[i].dst(ballPos) / 800) * 250;
								shootScore -= Math.pow((corners[i].cpy().sub(ballPos).angle() % 180 - 90)/5, 2);
								for (Player p: theirTeam) {
									if (between(ballPos, corners[i], p, 10)) shootScore -= 50;
								}
								float midGoalDist = this.position.dst(midGoal);
								for (Player p: myTeam) {
									if (midGoalDist > p.position.dst(midGoal) + 50) shootScore -= 20;
								}
								if (corners[i].x == 0) shootScore += 50;
							}
							if (shootScore >= bestShoot + Math.random() * 50 - 25) {
								bestShoot = shootScore;
								Vector2 cornerDir = corners[i].cpy().sub(ballPos).nor().scl(Math.max(400 + corners[i].dst(ballPos)/2, 700));
								cornerDir.rotate(8 - ((float) Math.random() * 16));
								shootCurl = ((float) Math.random() * 50) - 25;
								cornerDir.rotate(-shootCurl * cornerDir.len() / 2000);
								shootVect = new Vector3(cornerDir.x, cornerDir.y, (float) Math.pow(cornerDir.len(), 1.9)/(1600 + (float)Math.random() * 400));
							}
						}
						
						// Decide what to do based on scores
						midGoalAngle = midGoal.cpy().sub(this.position).angle();
						if (bestScore > setPiecePatience && bestScore > bestShoot) {
							System.out.println("Passing: " + bestScore + ", Shooting: " + bestShoot);
							System.out.println(bestDetails);
							ball.velocity = new Vector3(bestVector.x, bestVector.y, 0);
							this.possession = false;
							if (passTarget != null) {
								passTarget.runon = passTime + 0.5f;
							}
							posPlayer = null;
							this.kicking = 0.5f;
							setPiece = false;
							setPieceTeam = -1;
							this.state = PlayerState.moving;
							lastTouch = this.team;
						}
						else if (bestShoot > setPiecePatience) {
							System.out.println("Shooting: " + bestShoot + ", Passing: " + bestScore);
							this.kicking = 0.8f;
							this.possession = false;
							posPlayer = null;
							ball.possession = -1;
							ball.velocity = shootVect;
							ball.curl = shootCurl;
							setPiece = false;
							setPieceTeam = -1;
							this.state = PlayerState.moving;
							lastTouch = this.team;
						}
						else if ((angCompare(this.angle, midGoalAngle) < 90 && this.position.y * (1 - (2 * this.team)) > 200) || setPiecePatience <= 0) {
							Vector2 clearDir = new Vector2(Math.max(midGoal.dst(ballPos)/3, 200), 0);
							clearDir.rotate(12 - ((float) Math.random() * 24));
							if (angCompare(this.angle, midGoalAngle) > 30) {
								clearDir.rotate(midAngle(this.angle, midGoalAngle));
							}
							else clearDir.rotate(midGoalAngle);
							ball.velocity = new Vector3(clearDir.x, clearDir.y, (float) Math.pow(clearDir.len(), 1.8)/(300 + (float)Math.random() * 200));
							this.kicking = 0.8f;
							this.possession = false;
							posPlayer = null;
							ball.possession = -1;
							ball.curl = ((float) Math.random() * 50) - 25;
							System.out.println("HOOF!!! " + "to goal: " + midGoalAngle + ", myDir: " + this.angle + ", result: " + midAngle(this.angle, midGoalAngle));
							lastTouch = this.team;
							setPiece = false;
							setPieceTeam = -1;
							this.state = PlayerState.moving;
						}		
						// Crossing Term
						else if (setPiecePatience < 30 && ball.position.y * ((this.team * 2) - 1) > 350) {
							Vector2 clearDir = midGoal.cpy().scl(0.85f).sub(this.position).scl(0.9f).limit(550);
							clearDir.rotate(6 - ((float) Math.random() * 12));
							ball.velocity = new Vector3(clearDir.x, clearDir.y, (float) Math.pow(clearDir.len(), 1.8)/(300 + (float)Math.random() * 200));
							this.kicking = 0.8f;
							this.possession = false;
							posPlayer = null;
							ball.possession = -1;
							ball.curl = ((float) Math.random() * 50) - 25;
							System.out.println("CROSS!!! " + "to goal: " + midGoalAngle + ", myDir: " + this.angle + ", result: " + midAngle(this.angle, midGoalAngle) + ", Impulse: " + ball.velocity.len());
							lastTouch = this.team;
							setPiece = false;
							setPieceTeam = -1;
							this.state = PlayerState.moving;							
						}
					}
					break;
				case toball:
					break;
				case closingangle:
					float posDist = Math.max(Math.min(ballPos.dst(myGoal) - 120, 300) - Math.max((ballPos.dst(myGoal) - 300)/2, 0), 10);
					this.posTarget = myGoal.cpy().add(ballPos.cpy().sub(myGoal).nor().scl(posDist)).add(new Vector2(ballPos.x/15, 0));
					Vector2 goalInters = new Vector2();
					Intersector.intersectLines(new Vector2(0, 1008 - (this.team * 2016)), new Vector2(10, 1008 - (this.team * 2016)), ballPos, new Vector2(ballPos.x + ball.velocity.x, ballPos.y + ball.velocity.y), goalInters);
					if (Math.abs(goalInters.x) < 180 && goalInters.dst(ballPos) < ball.velocity.len()/AIR_RESISTANCE && ball.velocity.len() > this.dirSpeed/1.5 &&
							ball.velocity.y * (1 - (this.team * 2)) > 0) {
						Vector2 meInters = new Vector2();
						Intersector.intersectLines(this.position, this.position.cpy().add(new Vector2(10, 0).rotate(this.angle + 90)), ballPos, new Vector2(ballPos.x + ball.velocity.x, ballPos.y + ball.velocity.y), meInters);
						float diveT = -(float)Math.log(1 - (TACKLE_SLOWDOWN * meInters.dst(this.position)/(this.dirSpeed * this.diveLength)))/TACKLE_SLOWDOWN;
						float ballSlowDown = GROUND_RESISTANCE;
						if (ball.position.z > 5 || Math.abs(ball.velocity.z) > 10) ballSlowDown = AIR_RESISTANCE;
						float interT = -(float)Math.log(1 - (ballSlowDown * meInters.dst(ballPos)/(new Vector2(ball.velocity.x, ball.velocity.y).len())))/ballSlowDown;
						if (interT < diveT + this.thinkTime){
							this.state = PlayerState.diving;
							float diveSpeed = (float) Math.min(meInters.dst(this.position) * TACKLE_SLOWDOWN / (1 - Math.exp(- TACKLE_SLOWDOWN * interT)), this.dirSpeed * this.diveLength);
							this.velocity = meInters.cpy().sub(this.position).nor().scl(diveSpeed);
							this.diving = 1f;
							System.out.println("DIVE!!! Speed = " + this.velocity.len() + ", time to target: " + interT + ", dive time: " + diveT);
//							divePos = this.position.cpy().add(this.velocity.cpy().scl((float) (1 - Math.exp(-TACKLE_SLOWDOWN * diveT)) / TACKLE_SLOWDOWN));
//							savePos = ballPos.cpy().add(new Vector2(ball.velocity.x, ball.velocity.y).scl((float) (1 - Math.exp(-ballSlowDown * interT)/ballSlowDown)));
						}
					}
					break;
				case diving:
					break;
				case holding:
					// Calculate passing scores
					bestScore = 0;
					bestVector = new Vector2(250,0).rotate(dirTarget.angle());
					passTarget = null;
					passTime = 0;
					ang = this.angle - 75 + (float) (5 * Math.random());
					passRequired = new Vector2();
					passEnd = bestVector.cpy();
					bestDetails = "";
					while (ang <= this.angle + 75) {
						for (Player p: this.myTeam) {
							String details = "";
							Vector2 intersect = new Vector2();
							Intersector.intersectLines(ballPos, ballPos.cpy().add(new Vector2(1, 0).rotate(ang)), p.position, p.position.cpy().add(p.velocity), intersect);
							passRequired = new Vector2(intersect.x - ball.position.x, intersect.y - ball.position.y).add(p.velocity.cpy().limit(p.SIZE/2));
							float time = (intersect.x - p.position.x) / p.velocity.x;
							float denom = 1 - (float) Math.exp(time * -GROUND_RESISTANCE);
							Vector2 passVector = passRequired.scl(GROUND_RESISTANCE/denom);
							if (time < 0.2f && time > 10 || passRequired.len() > 1000 || passRequired.len()/time < 100 ||
									passVector.len() * Math.exp(-GROUND_RESISTANCE * time) > 500  || angCompare(passRequired.angle(), ang) > 10) {
								details += "Ang: " + ang + " to " + p.name + ", rejected; pass angle" + passRequired.angle() + ", time: " + time + ", pass length:" + passRequired.len() + ", ratio: " + passRequired.len()/time + ", impulse: " + passVector.len() * Math.exp(-GROUND_RESISTANCE * time);
								if (PASSPAUSE) System.out.println(details);
								continue;
							}
							float newScore = (float) Math.exp(-Math.abs(800 - passVector.len())/500);
							details += "Ang: " + ang + " to " + p.name + ", base sc: " + newScore + " (" + passVector.len() + ")";
							for (Player q: this.theirTeam) {
								if (between(this, intersect, q, 10)) {
									newScore -= 1;
									details += " Int Pl: " + q.name + "=-1";
								}
								newScore -= Math.exp(-intersect.dst(q.position)/(q.dirSpeed*time));
								if (Math.exp(-intersect.dst(q.position)/(time*q.dirSpeed)) > 0.01f) details += " Prox pen: " + q.name + " " + Math.exp(-intersect.dst(q.position)/(time*q.dirSpeed));
								newScore += Math.exp(-q.position.dst(ballPos)/100);
								if (Math.exp(-q.position.dst(ballPos)/100) > 0.01f) details += " Press bon: " + q.name + " " + Math.exp(-q.position.dst(ballPos)/100);
							}
							if (Math.abs(intersect.x) > 640 || Math.abs(intersect.y) > 1024) {
								newScore -= 1;
								details += " Off pitch: -1";
							}
							if (p.equals(this.calledFor)) {
								newScore += 0.5;
								details += " Called for: +0.5";
							}
							if (PASSPAUSE) System.out.println(details + " Net: "+ newScore);
							if (newScore > bestScore) {
								bestScore = newScore;
								bestVector = passVector.cpy();
								passTarget = p;
								passTime = time;
								passEnd = intersect;
								bestDetails = details;
							}
						}
						ang += 2.5;
					}
					bestScore *= (210 - angCompare(passEnd.cpy().sub(this.position).angle(), goalTarget.cpy().sub(this.position).angle()))/1.4f;
					if (PASSPAUSE) {
						System.out.println(" Net best: " + bestScore);
						PASSPAUSE = false;
						PAUSED = true;
					}
										
					// Decide what to do based on scores
					if (setPiecePatience <= 0) {
						this.velocity = new Vector2();
						this.angle = 270 - (180 * this.team);
					}
					
					if (bestScore > 50 && this.controlDelay <= 0) {
						System.out.println("Passing: " + bestScore);
						System.out.println(bestDetails);
						ball.velocity = new Vector3(bestVector.x, bestVector.y, 0);
						ball.position.z = 0;
						this.possession = false;
						if (passTarget != null) {
							passTarget.runon = passTime + 0.5f;
						}
						posPlayer = null;
						this.kicking = 0.5f;
						lastTouch = this.team;
					}
					else if ((angCompare(this.angle, midGoalAngle) < 20 && Math.random()< 0.1f && this.controlDelay <= 0) || setPiecePatience <= 0) {
						Vector2 clearDir = new Vector2(Math.max(midGoal.dst(ballPos)/3, 200), 0);
						clearDir.rotate(12 - ((float) Math.random() * 24));
						if (angCompare(this.angle, midGoalAngle) > 30) {
							clearDir.rotate(midAngle(this.angle, midGoalAngle));
						}
						else clearDir.rotate(midGoalAngle);
						ball.velocity = new Vector3(clearDir.x, clearDir.y, (float) Math.pow(clearDir.len(), 1.8)/(300 + (float)Math.random() * 200));
						this.kicking = 0.8f;
						this.possession = false;
						posPlayer = null;
						ball.possession = -1;
						ball.curl = ((float) Math.random() * 50) - 25;
						System.out.println("HOOF!!! " + "to goal: " + midGoalAngle + ", myDir: " + this.angle + ", result: " + midAngle(this.angle, midGoalAngle) + ", Impulse: " + ball.velocity.len());
						lastTouch = this.team;
					}
					else {
						this.posTarget = this.position.cpy().add(goalTarget.cpy().sub(this.position).nor().scl(10));
					
					}
					break;
				}
			}
			// KEEP PLAYER IN BOUNDS AND CHANGE VELOCITY
			if (Math.abs(this.posTarget.x) > 672) this.posTarget.x = 672 * Math.signum(this.posTarget.x);
			if (Math.abs(this.posTarget.y) > 1056) this.posTarget.y = 1056 * Math.signum(this.posTarget.y);
			if (Math.abs(this.posTarget.x) > 250) this.posTarget.x = 250 * Math.signum(this.posTarget.x);
			if (this.posTarget.y * (1-(2 * this.team)) < 700) this.posTarget.y = 700 * (1 - (2 * this.team));
			if (this.kicking <= 0 && this.tackling <= 0 && this.stun <= 0 && this.runon <= 0 && this.diving <= 0 && this.state != PlayerState.setpiecetaking) {
				if (this.position.dst(this.posTarget) > 10)	this.velocity = this.velocity.lerp(this.posTarget.cpy().sub(this.position).nor().scl(this.dirSpeed * this.runSpeed), 0.05f);
				}
			if (this.diving <= 0 && this.tackling <= 0) this.velocity.limit(this.dirSpeed * this.runSpeed);
			this.position.add(this.velocity.cpy().scl(deltaTime));
			if (this.kicking > 0 || this.tackling > 0  || this.diving > 0) this.velocity.scl(1 - (deltaTime * TACKLE_SLOWDOWN));
			if (this.stun > 0) this.velocity.scl(0.9f);
			// impact on ball
			if (setPiece) {}
			else if (this.state == PlayerState.holding) {}
			else if (this.state == PlayerState.dribbling && lastTouch != this.team) {}
			else if (((this.position.dst(ball.position.x, ball.position.y) < this.SIZE * CONTROL * 0.7) || (this.diving > 0 && this.position.dst(ball.position.x, ball.position.y) < this.SIZE * CONTROL * 0.75)) &&
					ball.position.z < 100 && this.velocity.cpy().add(new Vector2(ball.velocity.x, ball.velocity.y)).len() > this.controlSpeed && this.kicking <= 0) {	
				float impulse = this.velocity.cpy().add(new Vector2(ball.velocity.x, ball.velocity.y)).len();
				float restAngle = (2 * this.position.cpy().sub(ballPos).angle()) - new Vector2(ball.velocity.x, ball.velocity.y).angle() - 180;
				Vector3 axisVect = new Vector3(0, 0, 1).rotate((float)Math.random() * 30, (float)Math.random(), (float)Math.random(), 0);
				float ricochet = 0;
				if (this.diving > 0) ricochet = RICOCHET_COEFFICIENT / 3;
				else ricochet = RICOCHET_COEFFICIENT;
				ball.velocity = new Vector3(impulse * ricochet, 0, 0).rotate(axisVect, restAngle);
				if (this.diving > 0) ball.velocity.add(new Vector3(this.velocity.x * 2, this.velocity.y * 2, 0));
				lastTouch = this.team;
			}			
			else if (this.position.dst(ball.position.x, ball.position.y) < this.SIZE * CONTROL * 0.75 && this.stun <= 0 && this.kicking <= 0 && ball.position.z < 100 &&
					this.velocity.cpy().add(new Vector2(ball.velocity.x, ball.velocity.y)).len() < this.controlSpeed ) {
				Vector2 bestBallDir = this.position.cpy().add(new Vector2(this.posTarget.cpy().sub(this.position).nor().scl(SIZE + CONTROL)))
						.sub(new Vector2(ball.position.x, ball.position.y)).scl(1).limit(this.dirSpeed * this.runSpeed * 1.5f);
				ball.velocity.add(new Vector3(bestBallDir.x, bestBallDir.y, -ball.velocity.z * 0.5f));
				if (lastTouch != this.team) {
					System.out.println("Ball received at : ");
					lastTouch = this.team;
				}
			}
			this.kicking = Math.max(this.kicking - deltaTime, 0);
			this.tackling = Math.max(this.tackling - deltaTime, 0);
			this.diving = Math.max(this.diving - deltaTime, 0);
			if (this.runon > 0) {
				this.runon -= deltaTime;
				if (this.runon <= 0) {
					this.runon = 0;
					ball.possession = -1;
					passedTo = null;
				}
			}
			this.stun = Math.max(0, this.stun - deltaTime);
			this.label = this.name + ": " + this.state.toString();
		}
	}
			
	public class Ball {
		
		static final float SIZE = 16;
		Vector3 position = new Vector3();
		Vector3 velocity = new Vector3();
		Vector3 oldVelocity = new Vector3();
		float angle;
		float stateTime = 0;
		float roll = 0;
		float curl = 0;
		float rotation = 0;
		int possession = -1;
		Rectangle bounds = new Rectangle();

		public Ball(Vector3 position) {
			this.position = position;
			this.bounds.height = SIZE;
			this.bounds.width = SIZE;
			this.velocity = new Vector3();
			this.angle = new Vector2(this.velocity.x, this.velocity.y).angle();
		}

		private void update(float deltaTime) {
			this.oldVelocity = this.velocity.cpy();
			if(deltaTime == 0) return;
			this.roll += Math.sqrt(new Vector2(this.velocity.x, this.velocity.y).len()) * deltaTime * 10;
			if (Math.abs(this.position.y) > 1008 && Math.abs(this.position.y) < 1056 && Math.abs(this.position.x) < 150 &&
					this.position.z < 100 && this.position.z > 80 && this.velocity.z > 0) this.velocity.z = 0;
			if (Math.abs(this.position.y) > 1008  && Math.abs(this.position.y) < 1056 &&
					Math.abs(Math.abs(this.position.x) - 150) < 20) {
				this.velocity.x *= (0.8 - Math.exp((-Math.abs(Math.abs(this.position.x) - 1056)/10)));
				this.velocity.y *= 0.95;
			}
			if (Math.abs(this.position.x) < 150 && this.position.z < 100) {
				if (Math.abs(this.position.y) < 1016) {
					if (Math.abs(this.position.add(this.velocity.cpy().scl(deltaTime)).y) > 1016  && waitTime <= 0) {
						if (this.position.y > 0) score[1] ++;
						else score[0] ++;
						System.out.println("GOAL!!!");
						waitTime = 2;
						restartPosition = new Vector3(0, 0, 0);
						setPiece = true;
						setPiecePatience = 50;
						setPieceType = 0;
						if (ball.position.y < 0) setPieceTeam = 1;
						else setPieceTeam = 0;
					}
				}
				else if (Math.abs(Math.abs(this.position.y) - 1056) < 20) {
					this.velocity.y *= (0.8 - Math.exp((-Math.abs(Math.abs(this.position.y) - 1056)/10)));
					this.velocity.x *= 0.95;
					this.position.add(this.velocity.cpy().scl(deltaTime));
				}
				else this.position.add(this.velocity.cpy().scl(deltaTime));
			}
			else if (Math.abs(this.position.x) < 624  && Math.abs(this.position.cpy().add(this.velocity.cpy().scl(deltaTime)).x) >= 624 &&
					waitTime <= 0) {
				System.out.println("KICK IN");
				waitTime = 1.2f;
				restartPosition = new Vector3(this.position.x, this.position.y, 0);
				setPiece = true;
				setPiecePatience = 50;
				setPieceTeam = 1 - lastTouch;
				setPieceType = 1;
				this.position.add(this.velocity.cpy().scl(deltaTime));
			}
			else if(Math.abs(this.position.y) < 1008 && Math.abs(this.position.cpy().add(this.velocity.cpy().scl(deltaTime)).y) >= 1008 &&
					waitTime <= 0) {
				if (lastTouch == (int) ((Math.signum(this.position.y) + 1) / 2)) {
					System.out.println("GOAL KICK");
					waitTime = 1.2f;
					restartPosition = new Vector3(170 * Math.signum(this.position.x), 928 * Math.signum(this.position.y), 0);
					setPiece = true;
					setPiecePatience = 50;
					setPieceType = 2;
					setPieceTeam = 1 - lastTouch;
				}
				else {
					System.out.println("CORNER");
					waitTime = 1.2f;
					restartPosition = new Vector3(624 * Math.signum(this.position.x), 1007 * Math.signum(this.position.y), 0);
					setPiece = true;
					setPiecePatience = 50;
					setPieceType = 3;
					setPieceTeam = 1 - lastTouch;						
				}
				this.position.add(this.velocity.cpy().scl(deltaTime));
			}
			else this.position.add(this.velocity.cpy().scl(deltaTime));
			if (this.position.z < 1) {
				this.velocity.x *= (1 - (GROUND_RESISTANCE * deltaTime));
				this.velocity.y *= (1 - (GROUND_RESISTANCE * deltaTime));
				this.curl *= 0.75;
			}
			else {
				this.velocity.x *= (1 - (AIR_RESISTANCE * deltaTime));
				this.velocity.y *= (1 - (AIR_RESISTANCE * deltaTime));
				this.velocity.z *= (1 - (AIR_RESISTANCE * deltaTime));
				this.curl *= 0.995;
			}
			if (Math.abs(this.curl) < 15) this.rotation += (180 - myMod(this.rotation - 180, 360)) * 0.15;
			if (this.position.z < 0) {
				this.velocity.z = this.velocity.z * -BOUNCINESS;
				this.position.z = 0;
			}
			this.velocity.z = this.velocity.z - (GRAVITY * deltaTime);
			if(Gdx.input.isKeyPressed(Keys.T)) {
				this.velocity.x = (0.5f - (float) Math.random()) * 800;
				this.velocity.y = (0.5f - (float) Math.random()) * 800;
				this.velocity.z = (float) Math.random() * 200;
			}
			this.angle = new Vector2(this.velocity.x, this.velocity.y).angle() + 180 % 360;
			if (this.position.x < -704 || this.position.x > 704) {
				this.position.x -= this.velocity.x * deltaTime;
				this.velocity.x = -0.8f * this.velocity.x;
			}
			if (this.position.y < -1088 || this.position.y > 1088) {
				this.position.y -= this.velocity.y * deltaTime;
				this.velocity.y = -0.8f * this.velocity.y;
			}
			this.rotation =  (this.rotation + this.curl * deltaTime * 25) % 360;
			this.velocity.rotate(new Vector3(0, 0, 1), this.curl * deltaTime);
//			System.out.println(" " + this.position);
		}

		private void render(float deltaTime) {
			TextureRegion frame = null;
			frame = ballAnim.getKeyFrame(ball.roll);
			batch.draw(shadow, this.position.x - Ball.SIZE/2 + this.position.z / 10, this.position.y - Ball.SIZE/2 + this.position.z / 10, Ball.SIZE/2, Ball.SIZE/2, Ball.SIZE, Ball.SIZE, 1, 1, 0);
			batch.draw(frame, this.position.x - Ball.SIZE/2, this.position.y - Ball.SIZE/2, Ball.SIZE/2, Ball.SIZE/2, Ball.SIZE, Ball.SIZE, (1f + this.position.z/150), (1f + this.position.z/150), this.angle + this.rotation);
		}
		
		public ArrayList<Player> getPlayerList(int team) {
			ArrayList<Player> relPlayers = new ArrayList<Player>(5);
			if (users != null) {
				for (User u: users) {
					if (team < 0 || team == u.team) {
						relPlayers.add(u);
					}
				}
			}
			for (ComPlayer c: complayers) {
				if (c != null) {
					if (team < 0 || team == c.team){
						relPlayers.add(c);					
					}
				}
			}
			Collections.sort(relPlayers, new ComPlayer(new Vector2(this.position.x, this.position.y), 0, "Barry", new Vector2(0,0)));
			return relPlayers;
		}
		
		public int inBox() {
			if (Math.abs(this.position.x) > 200) return -1;
			else {
				if (this.position.y > 800) return 0;
				else if (this.position.y < -800) return 1;
				else return -1;
			}
		}
	}

	private static final float GRAVITY = 400f;
	private static final float CONTROL = 0.7f;
	private static final float GROUND_RESISTANCE = 1.3f;
	private static final float AIR_RESISTANCE = 0.4f;
	private static final float BOUNCINESS = 0.85f;
	private static final float CAM_SPEED = 8f;
	private static final float PL_BOUNCE = 0.5f;
	private static final int CHASERS = 1;
	private static final int SUPPORT = 2;
	private static final float DRIB_PEN = 0.85f;
	private static final float RICOCHET_COEFFICIENT = 0.5f;
	private static final float CONTROL_DELAY = 0.2f;
	private static final float TACKLE_SLOWDOWN = 2;
	private static final float TAKEOVER_DELAY = 15;
	private static final float SCREEN_FACTOR = 1.5f;    //  NEEDS TO BE 1.0 FOR ANDROID, 1.5 FOR DESKTOP
	private static final float USER_MISCONTROL_TIME = 0.5f;
	private OrthographicCamera camera;
	private float screenWidth;
	private float screenHeight;
	private SpriteBatch batch;
	private Texture texture;
	private Texture markings;
	private Texture playerTexture;
	private Texture ballTexture;
	private Texture ballShadow;
	private Texture goala, goalb;
	private Texture arrowTexture;
	private TextureRegion shadow;
	private TextureRegion arrow;
	private TextureRegion region;
	private Array<Animation> playerRuns = new Array<Animation>(true, 24);
	private Array<Animation> playerStopped = new Array<Animation>(true, 24);
	private Array<Animation> playerKicks = new Array<Animation>(true, 24);
	private Array<Animation> playerFalls = new Array<Animation>(true, 24);
	private Animation ballAnim = new Animation(7);
	private static int[] teamSizes = {2, 1};
	private User[] users;
	private ComPlayer[] complayers;
	private ComPlayer[] allplayers;
	private Ball ball;
	public List<ArrayList<Player>> ballPlayers;
	private int[][][] spriteAtlas;
	private Rectangle glViewPort;
	private BitmapFont font;
	private Matrix4 normalProjection;
	private String messagea, messageb;
	boolean PAUSED = false;
	boolean PASSPAUSE = false;
	boolean wasPressed = false;
	float waitTime = 0;
	boolean setPiece = false;
	int setPieceTeam = -1;
	int setPieceType = 0; // 0 - CENTRE, 1 - KICK-IN, 2 - GOAL KICK, 3 - CORNER, 4 - FREE KICK, 5 - PENALTY
	int lastTouch = 0;
	float setPiecePatience = 0;
	Vector3 restartPosition;
	private Player posPlayer;
	private ShapeRenderer shapeRenderer;
	Vector2[] posts = {new Vector2(-140, 1008), new Vector2(140, 1008), new Vector2(-140, -1008), new Vector2(140, -1008)};
	Vector2[] corners = {new Vector2(-130, 1008), new Vector2(130, 1008), new Vector2(0, 1008), new Vector2(-130, -1008), new Vector2(130, -1008), new Vector2(0, -1008)};
	Vector2 passEnd = null;
	Vector2 screenTouch = null;
	Player passedTo = null;
	float masterTime = 0;
	float touchCount = 0;
	Vector2 touchStart = new Vector2();
	List<String> names = Arrays.asList("Clive", "Brian", "Roger", "Iain", "Clifford", "Brenda", "Arnold", "Stan", "Percy", "Neville", "Olaf", "Frank");
	int score[] = {0, 0};
	Vector2 divePos = null;
	Vector2 savePos = null;

	@Override
	public void create() {
		
		shapeRenderer = new ShapeRenderer();
		
		Texture.setEnforcePotImages(false);
		screenWidth = Gdx.graphics.getWidth();
		screenHeight = Gdx.graphics.getHeight();
		glViewPort = new Rectangle(0, 0, screenWidth, screenHeight);
		font = new BitmapFont(Gdx.files.internal("data/font16.fnt"),
		         Gdx.files.internal("data/font16.png"), false);
		normalProjection = new Matrix4().setToOrtho2D(0, 0, Gdx.graphics.getWidth(),  Gdx.graphics.getHeight());
		messagea = "";
		messageb = "";
		
		camera = new OrthographicCamera(screenWidth * SCREEN_FACTOR, screenHeight * SCREEN_FACTOR);
		batch = new SpriteBatch();
		
        users = new User[1];
        users[0] = new User(new Vector2(100, 100), 0, "Richard", new Vector2(100, 100));
		users[0].velocity = new Vector2(0, 0);
		
//		complayers = new ComPlayer[teamSizes[0] + teamSizes[1] - 1];
		complayers = new ComPlayer[teamSizes[0] + teamSizes[1] - 1]; // CHANGE TO 0 FOR NO USER
		allplayers = new ComPlayer[teamSizes[0] + teamSizes[1]];
		for (int i = 0; i < teamSizes[1] - 1; i++) {
			Vector2 pos = new Vector2((float)Math.random() * 500 - 250, 600 - (i * 320));
			complayers[i] = new ComPlayer(pos, 1, names.get(i), pos.cpy());
			complayers[i].dirSpeed = 160;
			allplayers[i] = complayers[i];
		}
//		for (int i = teamSizes[1]; i < teamSizes[0] + teamSizes[1] - 1; i++) {
		for (int i = teamSizes[1]; i < teamSizes[0] + teamSizes[1] - 2; i++) { // CHANGE TO 1 FOR NO USER
			Vector2 pos = new Vector2((float)Math.random() * 500 - 250, -600 + ((i- teamSizes[1]) * 320));
			complayers[i] = new ComPlayer(pos, 0, names.get(i), pos.cpy());
			complayers[i].dirSpeed = 160;
			allplayers[i] = complayers[i];
		}
		complayers[teamSizes[1] - 1] = new Goalkeeper(new Vector2(0, -900), 1, names.get(teamSizes[1] - 1), new Vector2(0, -850));
		complayers[teamSizes[1] - 1].thinkTime = 0.15f;
		complayers[teamSizes[1] - 1].controlSpeed = 300;
		complayers[teamSizes[1] - 1].dirSpeed = 160;
		allplayers[teamSizes[1] - 1] = complayers[teamSizes[1] - 1];
		complayers[teamSizes[0] + teamSizes[1] - 2] = new Goalkeeper(new Vector2(0, 900), 0, names.get(teamSizes[0] + teamSizes[1] - 1), new Vector2(0, 850));
		complayers[teamSizes[0] + teamSizes[1] - 2].dirSpeed = 160;
		complayers[teamSizes[0] + teamSizes[1] - 2].thinkTime = 0.15f;
		complayers[teamSizes[0] + teamSizes[1] - 2].controlSpeed = 300;
		allplayers[teamSizes[0] + teamSizes[1] - 1] = complayers[teamSizes[0] + teamSizes[1] - 2];
		allplayers[teamSizes[0] + teamSizes[1] - 2] = users[0];
		ballPlayers = new ArrayList<ArrayList<Player>>(3);
		posPlayer = null;
		
		ball = new Ball(new Vector3(0, 0, 0));

		String spriteRaw = Gdx.files.internal("data/spriteatlas.dat").readString();
		spriteAtlas = readArrayFromString(spriteRaw, 8, 12, 2);
		
		texture = new Texture(Gdx.files.internal("data/pitch.png"));
		markings = new Texture(Gdx.files.internal("data/pitchmarkings.png"));
		playerTexture = new Texture(Gdx.files.internal("data/spritesheet.png"));
		ballTexture = new Texture(Gdx.files.internal("data/ui_ball.png"));
		ballShadow = new Texture(Gdx.files.internal("data/ballshadow.png"));
		goala = new Texture(Gdx.files.internal("data/goala.png"));
		goalb = new Texture(Gdx.files.internal("data/goalb.png"));
		shadow = new TextureRegion(ballShadow);
		arrowTexture = new Texture(Gdx.files.internal("data/arrow.png"));
		arrow = new TextureRegion(arrowTexture);
		TextureRegion[][] regions = TextureRegion.split(playerTexture, 32, 32);
		TextureRegion[][] ballTextures = TextureRegion.split(ballTexture, 64, 64);
		for (int i=0; i<8; i++) {
			Array<TextureRegion> spriteArray = new Array<TextureRegion>(true, 7);
			for (int j=0; j<7; j++) {
				spriteArray.add(regions[spriteAtlas[i][j][1]][spriteAtlas[i][j][0]]);
			}
			playerRuns.add(new Animation(15f, spriteArray));
			playerRuns.get(i).setPlayMode(Animation.LOOP_PINGPONG);
			playerStopped.add(new Animation(1f, regions[spriteAtlas[i][7][1]][spriteAtlas[i][7][0]]));
			playerStopped.get(i).setPlayMode(Animation.NORMAL);
			playerKicks.add(new Animation(1f, regions[spriteAtlas[i][8][1]][spriteAtlas[i][8][0]]));
			playerKicks.get(i).setPlayMode(Animation.NORMAL);
			spriteArray = new Array<TextureRegion>(true, 3);
			for (int j=9; j<12; j++) {
				spriteArray.add(regions[spriteAtlas[i][j][1]][spriteAtlas[i][j][0]]);
			}
			playerFalls.add(new Animation(0.15f, spriteArray));
			playerFalls.get(i).setPlayMode(Animation.NORMAL);
		}
		for (int i=0; i<8; i++) {
			Array<TextureRegion> spriteArray = new Array<TextureRegion>(true, 7);
			for (int j=0; j<7; j++) {
				spriteArray.add(regions[spriteAtlas[i][j][1]+5][spriteAtlas[i][j][0]]);
			}
			playerRuns.add(new Animation(15f, spriteArray));
			playerRuns.get(i+8).setPlayMode(Animation.LOOP_PINGPONG);
			playerStopped.add(new Animation(1f, regions[spriteAtlas[i][7][1]+5][spriteAtlas[i][7][0]]));
			playerStopped.get(i+8).setPlayMode(Animation.NORMAL);
			playerKicks.add(new Animation(1f, regions[spriteAtlas[i][8][1]+5][spriteAtlas[i][8][0]]));
			playerKicks.get(i+8).setPlayMode(Animation.NORMAL);	
			spriteArray = new Array<TextureRegion>(true, 3);
			for (int j=9; j<12; j++) {
				spriteArray.add(regions[spriteAtlas[i][j][1]+5][spriteAtlas[i][j][0]]);
			}
			playerFalls.add(new Animation(4f, spriteArray));
			playerFalls.get(i+8).setPlayMode(Animation.NORMAL);
		}
		for (int i=0; i<8; i++) {
			Array<TextureRegion> spriteArray = new Array<TextureRegion>(true, 7);
			for (int j=0; j<7; j++) {
				spriteArray.add(regions[spriteAtlas[i][j][1]+10][spriteAtlas[i][j][0]]);
			}
			playerRuns.add(new Animation(15f, spriteArray));
			playerRuns.get(i+16).setPlayMode(Animation.LOOP_PINGPONG);
			playerStopped.add(new Animation(1f, regions[spriteAtlas[i][7][1]+10][spriteAtlas[i][7][0]]));
			playerStopped.get(i+16).setPlayMode(Animation.NORMAL);
			playerKicks.add(new Animation(1f, regions[spriteAtlas[i][8][1]+10][spriteAtlas[i][8][0]]));
			playerKicks.get(i+16).setPlayMode(Animation.NORMAL);	
			spriteArray = new Array<TextureRegion>(true, 3);
			for (int j=9; j<12; j++) {
				spriteArray.add(regions[spriteAtlas[i][j][1]+10][spriteAtlas[i][j][0]]);
			}
			playerFalls.add(new Animation(4f, spriteArray));
			playerFalls.get(i+16).setPlayMode(Animation.NORMAL);
		}
		ballAnim = new Animation(6f, ballTextures[0][0], ballTextures[1][0], ballTextures[1][0], ballTextures[1][1], ballTextures[2][0], ballTextures[2][1], ballTextures[3][0]);
		ballAnim.setPlayMode(Animation.LOOP);
		texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
		texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
		markings.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
		markings.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
		
		region = new TextureRegion(texture, -1024, -1024, 2048, 2048);
		
		ballPlayers.add(ball.getPlayerList(0));
		ballPlayers.add(ball.getPlayerList(1));
		ballPlayers.add(ball.getPlayerList(-1));
		Player centreTaker = ballPlayers.get(1).get(0);
		ComPlayer cTaker = null;
		for (ComPlayer c: complayers) if (c.equals(centreTaker)) cTaker = c;
		cTaker.position = setpiecePos(ball.position, new Vector2(0, -1008 + (2016*1)));
		cTaker.state = PlayerState.setpiecetaking;
		cTaker.angle = new Vector2(0, -1008 + (2016*1)).sub(cTaker.position).angle();
		cTaker.possession = true;
		posPlayer = cTaker;
		ball.possession = cTaker.team;
		setPiece = true;
		setPiecePatience = 50;
	}

	@Override
	public void dispose() {
		batch.dispose();
		texture.dispose();
		playerTexture.dispose();
		ballTexture.dispose();
        }

	@Override
	public void render() {
		if (!PAUSED) {
			Gdx.gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
			Gdx.gl.glViewport((int) glViewPort.x, (int) glViewPort.y, (int) glViewPort.width, (int) glViewPort.height);
			float deltaTime = Gdx.graphics.getDeltaTime();
			masterTime += deltaTime;
			if (setPiecePatience < 40) setPiecePatience -= deltaTime;
			if (setPiecePatience < 35) setPiecePatience -= deltaTime;
			if (setPiecePatience < 30) setPiecePatience -= deltaTime;
			if (setPiecePatience < 25) setPiecePatience -= deltaTime;
			if (setPiecePatience < 20) setPiecePatience -= deltaTime;
			if (setPiecePatience < 15) setPiecePatience -= deltaTime;
			if (setPiecePatience < 10) setPiecePatience -= deltaTime;
			if (setPiecePatience < 5) setPiecePatience -= deltaTime;
			setPiecePatience = Math.max(setPiecePatience-deltaTime, 0);
			if (waitTime > 0) {
				waitTime -= deltaTime;
				if (waitTime <= 0) {
					waitTime = 0;
					ball.position = restartPosition.cpy();
					ball.velocity = new Vector3(0,0,0);
					ball.curl = 0;
					if (setPiece) {
						Player sTaker = null;
						float closestDist = 3000;
						for (Player c: allplayers) {
							if (c.team == setPieceTeam && setPieceType == 2 && c instanceof Goalkeeper) {
								sTaker = c;
								closestDist = 0;
							}
							else if (c.team == setPieceTeam &&
									!(c instanceof Goalkeeper && Arrays.binarySearch(new int[]{0, 1, 3, 5}, setPieceType) != -1)) {
								float thisDist = c.formation.dst(restartPosition.x, restartPosition.y);
								if (thisDist < closestDist) {
									sTaker = c;
									closestDist = thisDist;
								}
							}
						}
						if (sTaker == null) {
							setPieceTeam = 1-setPieceTeam;
							sTaker = ballPlayers.get(setPieceTeam).get(0);
						}
						if (sTaker instanceof User) {}
						else {
							ComPlayer cTaker = null;
							for (ComPlayer c: complayers) if (c.equals(sTaker)) cTaker = c;
							cTaker.position = setpiecePos(ball.position, new Vector2(0, -1008 + (2016*setPieceTeam)));
							cTaker.state = PlayerState.setpiecetaking;
							cTaker.angle = new Vector2(0, -1008 + (2016*setPieceTeam)).sub(cTaker.position).angle();
							cTaker.possession = true;
							posPlayer = cTaker;
							ball.possession = cTaker.team;
							setPiece = true;
						}
						System.out.println("Set Piece for team: " + setPieceTeam);
					}
				}
			}
			messagea = "Score: " + score[0] + " - " + score[1];
			messageb = "Game Time: " + (int) masterTime;
			ballPlayers.set(0, ball.getPlayerList(0));
			ballPlayers.set(1, ball.getPlayerList(1));
			ballPlayers.set(2, ball.getPlayerList(-1));
			Player closePlayer = ballPlayers.get(2).get(0);
			// REASSIGN POSSESSION
			if (posPlayer != null && posPlayer instanceof Goalkeeper) {	
				if ((posPlayer.position.dst(new Vector2(ball.position.x, ball.position.y)) > (closePlayer.SIZE) * 0.7)  || ball.position.z > 100) {
					posPlayer = null;
					clearPossession();
				}
			}
			else if ((closePlayer.position.dst(new Vector2(ball.position.x, ball.position.y)) > (closePlayer.SIZE + Ball.SIZE) * 0.8)  || ball.position.z > 100) {
				posPlayer = null;
				clearPossession();
			}
			else if (closePlayer.kicking <= 0 && closePlayer.tackling <= 0 && closePlayer.stun <= 0 && !(closePlayer instanceof Goalkeeper) && !setPiece) {
				if (!closePlayer.equals(posPlayer)) {
					ComPlayer newDribbler = null;
					for (ComPlayer c: complayers) if (c.equals(closePlayer)) newDribbler = c;
					if (newDribbler != null) {
						newDribbler.controlDelay = CONTROL_DELAY;
						if (newDribbler instanceof Goalkeeper) newDribbler.controlDelay *= 4;
					}
				}
				ball.possession = closePlayer.team;
				posPlayer = closePlayer;
				closePlayer.possession = true;
			}
			else if (closePlayer.kicking <= 0 && closePlayer.tackling > 0 && closePlayer instanceof Goalkeeper && !setPiece) {
				ball.possession = closePlayer.team;
				posPlayer = closePlayer;
				closePlayer.possession = true;
			}
			// MAKE SURE SETPIECE DOESN'T GO WRONG
			boolean noTaker = true;
			for (ComPlayer p: complayers) if (p.state == PlayerState.setpiecetaking) noTaker = false;
			if (users != null) {
				for (User p: users) if (p.setPiece = false) noTaker = false;
			}
			if (noTaker && waitTime <= 0) setPiece = false;
			int runnerOffset = 0;
			for (Player p: ballPlayers.get(2)) if (p.runon > 0) runnerOffset += 1;
			// REASSIGN ROLES
			for (int team = 0; team < 2; team++) {
				ArrayList<Player> thisTeam = ballPlayers.get(team);
				if (ball.possession == team) {
					int supCount = 0;
					for (int i = 0; i < thisTeam.size(); i++) {
						ComPlayer thisPlayer = null;
						for (ComPlayer c: allplayers) if (c.equals(thisTeam.get(i))) thisPlayer = c;
						if (thisPlayer == null || (thisPlayer instanceof User  && ((User)thisPlayer).inputTime < TAKEOVER_DELAY)) continue;
						else if (thisPlayer instanceof User) {
							if (!thisPlayer.possession && ((User)thisPlayer).misControl > masterTime - USER_MISCONTROL_TIME) thisPlayer.state = PlayerState.chasing;
							else if (thisPlayer.possession) thisPlayer.state = PlayerState.dribbling; 
						}
						else if (thisPlayer.stun > 0) thisPlayer.state = PlayerState.stunned;
						else if (thisPlayer.runon > 0) thisPlayer.state = PlayerState.runningon;
						else if (thisPlayer.diving > 0) thisPlayer.state = PlayerState.diving;
						else if (waitTime > 0) thisPlayer.state = PlayerState.waiting;
						else if (thisPlayer.state == PlayerState.setpiecetaking) continue;
						else if (thisPlayer.possession) {
							if (thisPlayer instanceof Goalkeeper &&
									thisPlayer.position.dst(new Vector2(ball.position.x, ball.position.y)) < (thisPlayer.SIZE + Ball.SIZE) * 0.6) {
								if (thisPlayer.state != PlayerState.holding) {
									setPiecePatience = 20; 
									thisPlayer.state = PlayerState.holding;
									thisPlayer.runSpeed = 0.5f;
								}
							}
							else {
								thisPlayer.state = PlayerState.dribbling;
								thisPlayer.runSpeed = DRIB_PEN;
							}
						}
						else if (i == 0 && runnerOffset == 0) thisPlayer.state = PlayerState.chasing;
						else if (supCount < SUPPORT && !(thisPlayer instanceof Goalkeeper)) {
							thisPlayer.state = PlayerState.moving;
							supCount += 1;
						}
						else thisPlayer.state = PlayerState.positioning;
					}					
				}
				else {
					int chaseCount = 0;
					for (int i = 0; i < thisTeam.size(); i++) {
						ComPlayer thisPlayer = null;
						for (ComPlayer c: allplayers) if (c.equals(thisTeam.get(i))) thisPlayer = c;
						if (thisPlayer == null  || (thisPlayer instanceof User  && ((User)thisPlayer).inputTime < TAKEOVER_DELAY)) continue;
						else if (thisPlayer.stun > 0) thisPlayer.state = PlayerState.stunned;
						else if (thisPlayer.diving > 0) thisPlayer.state = PlayerState.diving;
						else if (waitTime > 0) thisPlayer.state = PlayerState.waiting;
						else if (chaseCount < CHASERS && (posPlayer == null || !(posPlayer instanceof Goalkeeper))) {
							if (!(thisPlayer instanceof Goalkeeper) || thisPlayer.position.dst(new Vector2(ball.position.x, ball.position.y)) < 100) {
								thisPlayer.state = PlayerState.chasing;
								chaseCount += 1;
							}
							else if (thisPlayer instanceof Goalkeeper) thisPlayer.state = PlayerState.closingangle;
							else thisPlayer.state = PlayerState.positioning;
						}
						else if (thisPlayer instanceof Goalkeeper && ball.inBox() > -1 && ballPlayers.get(2).get(0).team == thisPlayer.team) thisPlayer.state = PlayerState.positioning;
						else if (thisPlayer instanceof Goalkeeper &&
								new Vector2(ball.position.x, ball.position.y).dst(new Vector2(0, 1008 - (2016 * team))) < 800) thisPlayer.state = PlayerState.closingangle;
						else {
							thisPlayer.state = PlayerState.positioning;
						}
					}
				}
			}
			Collections.reverse(Arrays.asList(complayers));
			Collections.reverse(Arrays.asList(allplayers));
			for (ComPlayer c: allplayers) {
				if (c != null) {
					c.recalcPlayer(deltaTime);
				}
			}
			for (ComPlayer c: allplayers) {
				if (c != null) {
					if (c instanceof User) ((User)c).updateUser(deltaTime);
					else c.updatePlayer(deltaTime);
				}
			}				
			ball.update(deltaTime);
			collisionDetect(deltaTime);
			updateCamera(deltaTime);


			batch.setProjectionMatrix(camera.combined);
			batch.begin();

			batch.draw(region, -704, -1088, 1408, 2176);
			batch.draw(markings, -640f, -1024f, 1280f, 2048f, 0, 0, 256, 256, false, false);

			ball.render(deltaTime);
			if (users != null) {
				for (User u: users) {
					u.renderPlayer(deltaTime);
				}
			}
			for (ComPlayer c: complayers) {
				if (c != null) {
					c.renderPlayer(deltaTime);
				}
			}
			batch.draw(goala, -155, 1008, 310, 72);
			batch.draw(goalb, -165, -1056, 330, 72);
			batch.setProjectionMatrix(normalProjection);
			font.setColor(0.0f, 0.0f, 0.2f, 1.0f);
			font.draw(batch, messageb, 25, 25);
			font.draw(batch, messagea, 25, 50);
			batch.end();
			shapeRenderer.setProjectionMatrix(camera.combined);
			shapeRenderer.begin(ShapeType.Line);
			shapeRenderer.setColor(0, 0, 1, 1);
			if (passEnd != null) shapeRenderer.circle(passEnd.x, passEnd.y, 10);
			if (screenTouch != null) shapeRenderer.circle(screenTouch.x, screenTouch.y, 15);
			if (savePos != null) {
				shapeRenderer.setColor(1, 1, 0, 1);
				shapeRenderer.line(new Vector2(ball.position.x, ball.position.y), savePos);
				shapeRenderer.circle(divePos.x, divePos.y, 20);
				PAUSED = true;
			}
			shapeRenderer.end();
			if (Gdx.input.isKeyPressed(Keys.I)) {
				if (!wasPressed) {
					infoDump();
					if (posPlayer != null) PASSPAUSE = true;
					else PAUSED = true;
					wasPressed = true;
				}
			}
			else wasPressed = false;
		}
		else {
			if (Gdx.input.isKeyPressed(Keys.I)) {
				if (!wasPressed) {
					wasPressed = true;
					PAUSED = false;
					savePos = null;
					divePos = null;
				}
			}
			else wasPressed = false;
		}
	}
	
    @Override
    public void resize(
        int width,
        int height )
    {
    }

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}
	
	private void updateCamera(float deltaTime) {
//		Vector3 playPos = new Vector3(richard.position.x, richard.position.y, 0);
		Vector3 playPos = new Vector3(ball.position.x, ball.position.y, 0);
		float factor = playPos.dst2(camera.position) / 2500;
		if (factor > 0.01) {
			camera.translate(playPos.sub(camera.position).scl(factor).limit(CAM_SPEED));
		}
		camera.update();
	}

	private Vector2 screenToWorld(Vector2 screen) {
		return new Vector2((screen.x - screenWidth/2) * SCREEN_FACTOR + camera.position.x, (screen.y - screenHeight/2) * SCREEN_FACTOR + camera.position.y);
	}
	
	private void collisionDetect(float deltaTime) {
		for (int i=0; i<allplayers.length-1; i++) {
			for (int j=i+1; j<allplayers.length; j++) {
				if (allplayers[i].tackling + allplayers[j].tackling <= 0) {
					if (allplayers[i].position.dst(allplayers[j].position) < allplayers[i].SIZE * 0.6f) {
						Vector2 closeVect = allplayers[i].position.cpy().sub(allplayers[j].position).nor().scl(deltaTime*10);
						allplayers[i].position.add(closeVect);
						allplayers[j].position.sub(closeVect);
					}
					else if (allplayers[i].position.dst(allplayers[j].position) < allplayers[i].SIZE * 0.7f) {
						Vector2 eachMoment = allplayers[i].velocity.cpy().add(allplayers[j].velocity).scl(0.5f);
						Vector2 eachRej = vecReject(allplayers[i].velocity, eachMoment);
						allplayers[i].velocity = eachMoment.cpy().sub(eachRej.cpy().scl(PL_BOUNCE));
						allplayers[j].velocity = eachMoment.cpy().add(eachRej.scl(PL_BOUNCE));
						allplayers[i].position.sub(eachMoment.scl(deltaTime));
						allplayers[j].position.add(eachMoment.scl(deltaTime));
					}
				}
			}
		}
		for (Vector2 post: posts) {
			if (new Vector2(ball.position.x, ball.position.y).dst(post) < Ball.SIZE && ball.position.z < 100) {
				float Pang = new Vector2(post.x - ball.position.x, post.y - ball.position.y).angle();
				float beta = new Vector2(ball.velocity.x, ball.velocity.y).angle();
				ball.velocity.rotate((2 * Pang) - (2 * beta) - 180, 0, 0, 1);
				Vector2 postEdge = post.cpy().sub(new Vector2(Ball.SIZE, 0).rotate(Pang));
				ball.position = new Vector3(postEdge.x, postEdge.y, ball.position.z);
			}
		}
		if (Math.abs(ball.position.x)< 150 && new Vector2(ball.position.y, ball.position.z).dst(new Vector2(1008, 100)) < Ball.SIZE) {
			float Pang = new Vector2(1008 - ball.position.y, 100 - ball.position.z).angle();
			float beta = new Vector2(ball.velocity.y, ball.velocity.z).angle();
			ball.velocity.rotate((2 * Pang) - (2 * beta) - 180, 1, 0, 0);		
		}
		if (Math.abs(ball.position.x)< 150 && new Vector2(ball.position.y, ball.position.z).dst(new Vector2(-1008, 100)) < Ball.SIZE) {
			float Pang = new Vector2(-1008 - ball.position.y, 100 - ball.position.z).angle();
			float beta = new Vector2(ball.velocity.y, ball.velocity.z).angle();
			ball.velocity.rotate((2 * Pang) - (2 * beta) - 180, 1, 0, 0);		
		}	
	}
	
	private float myMod(float numer, float denom) {
		float output = numer % denom;
		if (output < 0) output += denom;
		return output;
	}
	
	public int[][][] readArrayFromString(String input, int x, int y, int z){
		BufferedReader ps;
		int[][][] resArray = new int[x][y][z];
		try {
			ps = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(input.getBytes())));
			for(int row=0;row < x;row++){
				for(int col=0; col < y;col++){
					for(int dep=0; dep < z;dep++) {
						resArray[row][col][dep] = Integer.parseInt(ps.readLine());
					}
				}
			}
			ps.close();
		} catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
		return resArray;
	}

	public Vector2 setpiecePos(Vector3 ballPos, Vector2 goalPos) {
		Vector2 ball2D = new Vector2(ballPos.x, ballPos.y);
		return ball2D.sub(goalPos.sub(ball2D).nor().scl(40));
	}
	
	public Vector2 vecReject(Vector2 a, Vector2 b) {
		Vector2 bhat = b.cpy().nor();
		float theta = b.angle() - a.angle();
		return a.sub(bhat.scl(a.len() * (float) Math.cos(theta)));
	}
	
	public void clearPossession() {
		for (ComPlayer c: allplayers) {
			if (c.possession) {
				c.possession = false;
				if (c instanceof User) ((User)c).misControl = masterTime;
//				if (c.tackling <= 0) c.stun = 1.1f;
			}
		}
	}

	public float angCompare(float a, float b) {
		return (float) Math.abs(((a - b + 180) % 360) - 180);
	}

	public float midAngle(float a, float b) {
		float newb = myMod(b-a+180,360) - 180;
		return myMod(a + (newb/2), 360);
	}
	
	public boolean offPitch(Vector2 p) {
		if (Math.abs(p.x) > 620) return true;
		if (Math.abs(p.y) > 1004) return true;
		return false;
	}
	
	public boolean between(Player a, Player b, Player c, float angle) {
		if (a.position.dst(b.position) < a.position.dst(c.position)) return false;
		if (angCompare(b.position.cpy().sub(a.position).angle(), c.position.cpy().sub(a.position).angle()) > angle) return false;
		return true;
	}
	
	public boolean between(Player a, Ball b, Player c, float angle) {
		if (a.position.dst(new Vector2(b.position.x, b.position.y)) < a.position.dst(c.position)) return false;
		if (angCompare(new Vector2(b.position.x, b.position.y).sub(a.position).angle(), c.position.cpy().sub(a.position).angle()) > angle) return false;
		return true;		
	}
	
	public boolean between(Player a, Vector2 b, Player c, float angle) {
		if (a.position.dst(b) < a.position.dst(c.position)) return false;
		if (angCompare(b.cpy().sub(a.position).angle(), c.position.cpy().sub(a.position).angle()) > angle) return false;
		return true;
	}	

	public boolean between(Vector2 a, Vector2 b, Player c, float angle) {
		if (a.dst(b) < a.dst(c.position)) return false;
		if (angCompare(b.cpy().sub(a).angle(), c.position.cpy().sub(a).angle()) > angle) return false;
		return true;		
	}	
	
	public void infoDump() {
		if (users != null) {
			for (User u: users) {
				System.out.print("Name:" + u.name + ", Team: " + u.team + ", Pos: " + u.position + ", Vel: " + u.velocity + ", Angle: " + u.angle + ", Input Time: " + u.inputTime);
				if (u.state != null) System.out.println(", state: " + u.state + ", Target: " + u.posTarget);
				else System.out.println("");
			}
		}
		for (ComPlayer c: complayers) {
			System.out.print("Name:" + c.name);
			if (c instanceof Goalkeeper) System.out.print("(GK)");
			System.out.println(", Team: " + c.team + ", Pos: " + c.position + ", Vel: " + c.velocity + ", Angle: " + c.angle + ", Target" + c.posTarget + ", State: " + c.state + ", Stun: " + c.stun + ", Kicking: " + c.kicking + ", Tackling: " + c.tackling + ", Anim: " + c.animDir + " [" + c.animTime + "]");
			if (c.state == PlayerState.chasing) System.out.println("Ball pos : " + ball.position + ", Target: " + c.posTarget);
			if (c.equals(posPlayer)) System.out.println("Has possession");
		}
		System.out.println("Possession: " + ball.possession + ", Set Piece: " + setPiece + " Set Piece Patience: " + setPiecePatience + ", Wait Time: " + waitTime);
	}
	
}