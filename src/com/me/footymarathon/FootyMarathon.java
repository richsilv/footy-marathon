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
		moving,
		runningon
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
		float angle;
		float stateTime = 0;
		float kicking = 0;
		float kickBuilder = 0;
		float tackling = 0;
		float stun = 0;
		float runon = 0;
		int team;
		String name;
		Rectangle bounds = new Rectangle();
		Vector2 formation = new Vector2();
		ArrayList<Player> myTeam;
		ArrayList<Player> theirTeam;
		Polar[] myTeamPolar;
		Polar[] theirTeamPolar;
		Player calledFor = null;
		
		public ArrayList<Player> getPlayerList(int team) {
			ArrayList<Player> relPlayers = new ArrayList<Player>(5);
			for (User u: users) {
				if (u.team == team && !u.equals(this)) {
					relPlayers.add(u);
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
			int dir = (int) (this.angle + 22.5) / 45 % 8;
			if (this.kicking > 0 || this.tackling > 0) {
				frame = playerKicks.get(dir + offset).getKeyFrame(0);
			}
			else if (this.velocity.len() < 0.5) {
				frame = playerStopped.get(dir + offset).getKeyFrame(0);
			}
			else {
				frame = playerRuns.get(dir + offset).getKeyFrame(this.stateTime);
			}
			if (this.position.dst(camera.position.x, camera.position.y) > 250) {
				Vector2 arrowVect = this.position.cpy().sub(camera.position.x, camera.position.y);
				Vector2 dispVect = arrowVect.cpy().nor().scl(200);
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
				font.draw(batch, this.label, this.position.x - 50, this.position.y + this.SIZE + 10);				
			}
		}
		
		public int compare(Player p1, Player p2) {
			return (int) (this.position.dst(p1.position) - this.position.dst(p2.position));
		}
	}
	
	public class User extends Player {
		
		public User(Vector2 position, int team, String name, Vector2 formation) {
			this.position = position;
			this.bounds.height = SIZE;
			this.bounds.width = SIZE;
			this.velocity = new Vector2();
			this.angle = this.velocity.angle();
			this.team = team;
			this.name = name;
			this.formation = formation;
			this.myTeamPolar = new Polar[teamSizes[this.team]];
			this.theirTeamPolar = new Polar[teamSizes[1 - this.team]];
		}

		public void recalcPlayer(float deltaTime) {
			if (deltaTime == 0) return;
			this.stateTime += deltaTime * this.velocity.len();
			this.myTeam = this.getPlayerList(this.team);
			this.theirTeam = this.getPlayerList(1 - this.team);
			for (Player p: this.myTeam) {
				this.myTeamPolar[this.myTeam.indexOf(p)] = new Polar(p.position.cpy().sub(this.position).angle(), p.position.dst(this.position));
			}
			for (Player p: this.theirTeam) {
				this.theirTeamPolar[this.theirTeam.indexOf(p)] = new Polar(p.position.cpy().sub(this.position).angle(), p.position.dst(this.position));
			}
		}
		
		public void updatePlayer(float deltaTime) {
			if(deltaTime == 0) return;
			float factor = 1 / (float) Math.sqrt(2);
			Vector2 dirTarget = new Vector2(1, 0).rotate(this.angle);
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
			}
			else {
				if(Gdx.input.isKeyPressed(Keys.UP)) {
					dirTarget = new Vector2(0, this.dirSpeed);
				}
				else if(Gdx.input.isKeyPressed(Keys.DOWN)) {
					dirTarget = new Vector2(0, -this.dirSpeed);
				}
				else {
					dirTarget = new Vector2(0, 0);
				}
			}
			if (Gdx.input.isKeyPressed(Keys.Z)) {
				if ((this.possession || this.kickBuilder > 0) && this.tackling <= 0) {
					this.kickBuilder += deltaTime;
				}
				else if (this.tackling == 0 && this.stun == 0) {
					this.tackling = 0.7f;
					this.velocity = dirTarget.cpy().nor().scl(this.dirSpeed * 1.5f);
				}
			}
			else {
				if (this.kickBuilder > 0) {
					if (this.possession) {
						this.possession = false;
						this.kicking = Math.min(this.kickBuilder,  0.5f) + 0.4f;
						messagea = "" + this.kicking;
						Vector2 kickDir = new Vector2(this.kicking, 0).rotate(this.angle);
						ball.velocity = new Vector3(kickDir.x, kickDir.y, (float) Math.pow(this.kicking, 1.5)).scl(1000);
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
					float ang = dirTarget.angle() - 30 + (float) (5 * Math.random());
					while (ang <= dirTarget.angle() + 30) {
						float tanval = (float) Math.tan(Math.toRadians(ang));
						System.out.println(ang + " " + tanval);
						for (Player p: this.myTeam) {
							Vector2 intersect = new Vector2();
							Intersector.intersectLines(new Vector2(ball.position.x, ball.position.y), new Vector2(ball.position.x, ball.position.y).add(new Vector2(1, 0).rotate(ang)), p.position, p.position.cpy().add(p.velocity), intersect);
							Vector2 passRequired = new Vector2(intersect.x - ball.position.x, intersect.y - ball.position.y);
							float time = (intersect.x - p.position.x) / p.velocity.x;
							float denom = 1 - (float) Math.exp(time * -GROUND_RESISTANCE);
							Vector2 passVector = passRequired.scl(GROUND_RESISTANCE/denom);
							float newScore = 1 - (float) Math.pow(1 - 600/passVector.len(), 2);
							for (Player q: this.theirTeam) {
								if (between(this, intersect, q, 10)) newScore -= 1;
							}
							if (Math.abs(intersect.x) > 640 || Math.abs(intersect.y) > 1024) newScore -= 1;
							if (newScore > bestScore && time > 0.2f && time < 10 && passRequired.len() < 1000 && passRequired.len()/time > 100) {
								bestScore = newScore;
								bestVector = passVector.cpy();
								passTarget = p;
								passTime = time;
								passAng = ang;
							}
						}
						ang += 5;
					}
					ball.velocity = new Vector3(bestVector.x, bestVector.y, 0);
					this.possession = false;
					if (passTarget != null) {
						passTarget.runon = passTime + 0.5f;
//						passedTo = passTarget;
//						passEnd = passTarget.position.cpy().add(passTarget.velocity.cpy().scl(passTime));
//						passStart = new Vector2(ball.position.x, ball.position.y);
//						passElapsed = 0;
//						passV0 = bestVector.cpy();
					}
					posPlayer = null;
					this.kicking = 0.5f;
				}
			}
			if (Gdx.input.isKeyPressed(Keys.C)){
				if (ball.possession == this.team && !this.possession) {
					for (Player p: this.myTeam) {
						if (p.possession) p.calledFor = this;
					}
				}
			}
			if (Gdx.input.isKeyPressed(Keys.I)) {
				infoDump();
				PAUSED = true;
			}
			if (this.kicking <= 0 && this.tackling <= 0 && this.stun <= 0) {
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
			if (this.velocity.len() > 0.1) this.angle = this.velocity.angle();
			if (this.velocity.len() < 0.1) {
				this.velocity = new Vector2();
			}
			this.position.add(this.velocity.cpy().scl(deltaTime));
			if (new Vector2(this.position.x, this.position.y).dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) / 1.5 
					&& this.kicking <= 0 && ball.position.z < 50 && ball.possession != (1 - this.team)) {
				clearPossession();
				this.possession = true;
				this.runSpeed = DRIB_PEN;
				ball.possession = this.team;
				posPlayer = this;
				ball.curl = 0;
				ball.rotation = 0;
			}
			if (this.possession) {
				if (this.position.dst(ball.position.x, ball.position.y) > (this.SIZE + Ball.SIZE) * 2.5) {
					this.possession = false;
					ball.possession = -1;
					posPlayer = null;
				}
				else if (this.position.dst(ball.position.x, ball.position.y) < this.SIZE * CONTROL * 0.75) {
//					Vector2 bestBallDir = this.position.cpy().add(new Vector2(this.SIZE * CONTROL, 0).rotate(this.angle))
//							.sub(new Vector2(ball.position.x, ball.position.y)).scl(1).limit(this.dirSpeed * 1.5f);
					Vector2 bestBallDir = this.position.cpy().add(dirTarget.nor().scl(SIZE + CONTROL))
							.sub(new Vector2(ball.position.x, ball.position.y)).scl(1).limit(this.dirSpeed * 1.5f);
					if (this.velocity.len() > 100) {
						ball.velocity.add(new Vector3(bestBallDir.x, bestBallDir.y, -ball.velocity.z * 0.5f));
					}
					else {
						ball.velocity.scl(0.8f);
					}
				}
			}
			if (this.kicking > 0 || this.tackling > 0) this.velocity.scl(0.95f);
			if (this.stun > 0) this.velocity.scl(0.9f);
			this.kicking = Math.max(this.kicking - deltaTime, 0);
			this.tackling = Math.max(this.tackling - deltaTime, 0);
			this.stun = Math.max(this.stun - deltaTime, 0);	
		}
	}
	
	public class ComPlayer extends Player{

		PlayerState state;
		Vector2 posTarget;
		float thinkTime = 0.25f;
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
			this.myTeam = this.getPlayerList(this.team);
			this.theirTeam = this.getPlayerList(1 - this.team);
			for (Player p: this.myTeam) {
				this.myTeamPolar[this.myTeam.indexOf(p)] = new Polar(p.position.cpy().sub(this.position).angle(), p.position.dst(this.position));
			}
			for (Player p: this.theirTeam) {
				this.theirTeamPolar[this.theirTeam.indexOf(p)] = new Polar(p.position.cpy().sub(this.position).angle(), p.position.dst(this.position));
			}
			//		System.out.println(myTeam);
			//		System.out.println(theirTeam);
			if (this.velocity.len() > 0.1) this.angle = this.velocity.angle();
			if (this.velocity.len() < 0.1) {
				this.velocity = new Vector2();
			}	
		}
		
		public void updatePlayer(float deltaTime) {
			if (!this.possession) this.runSpeed = 1;
			if(deltaTime == 0) return;
			this.label = "";
			Vector2 goalTarget = new Vector2(0, -980 + (this.team * 1920));
			Vector2 offset = new Vector2(ball.position.x, ball.position.y).sub(goalTarget).nor().scl(10);
//			if (this.possession) System.out.println(offset);
			Vector2 dirTarget = new Vector2(ball.position.x, ball.position.y).add(offset);
			// ACTIONS TO TAKE EVERY FRAME
			switch(this.state) {
			case chasing:
				if (new Vector2(this.position.x, this.position.y).dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.55
						&& this.kicking <= 0 && ball.position.z < 50) {
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
					}
					else if (this.tackling > 0) {
						ball.velocity.lerp(new Vector3(this.velocity.x, this.velocity.y, 2).scl(1.5f), 0.2f);
						if (posPlayer != null) {
							posPlayer.stun = 0.8f;
						}
					}
				}
				this.posTarget = dirTarget;
				if (posPlayer != null) {
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
				if (Math.abs(dirTarget.cpy().sub(this.position).angle() - this.angle) < 20 && new Vector2(ball.position.x, ball.position.y).dst(this.position) < this.SIZE &&
						this.theirTeam.size() > 0 && this.theirTeam.get(0).position.dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE * 1.3)) {
					boolean reject = false;
					for (Player p: theirTeam) {
						if (between(this, ball, p, 60) && p.position.dst(this.position) < 75) reject = true;
					}
					if (!reject) {
						this.tackling = 0.6f;
						this.velocity.nor().scl(this.dirSpeed * 1.5f);
					}
				}
				break;
			case dribbling:
				// LOSE CONTROL
				if (this.position.dst(ball.position.x, ball.position.y) > (this.SIZE + Ball.SIZE) * 0.8) {
//					System.out.println(this.thinkCounter);
					this.runSpeed = 1;
					this.possession = false;
					ball.possession = -1;
					posPlayer = null;
					this.state = PlayerState.chasing;
				}
				// PUSH BALL AHEAD
				else if (this.position.dst(ball.position.x, ball.position.y) < this.SIZE * CONTROL * 0.75) {
					Vector2 bestBallDir = this.position.cpy().add(new Vector2(this.posTarget.cpy().sub(this.position).nor().scl(SIZE + CONTROL)))
							.sub(new Vector2(ball.position.x, ball.position.y)).scl(1).limit(this.dirSpeed * this.runSpeed * 1.5f);
					ball.velocity.add(new Vector3(bestBallDir.x, bestBallDir.y, -ball.velocity.z * 0.5f));
				}
				if (this.position.dst(this.posTarget) < 5) this.posTarget = this.position;
				break;
			case moving:
				if (this.position.dst(this.posTarget) < 5) this.posTarget = this.position;
				break;
			case positioning:
				break;
			case marking:
				break;
			case stunned:
				break;
			case runningon:
				if (this.position.dst(new Vector2(ball.position.x, ball.position.y)) < (this.SIZE + Ball.SIZE) * 0.55
						&& this.kicking <= 0 && ball.position.z < 50) {
					if (this.tackling <= 0 && (ball.possession != (1-this.team)) &&
							this.velocity.cpy().sub(ball.velocity.x, ball.velocity.y).len() < 500) {
						clearPossession();
						this.possession = true;
						Vector2 controlVector = new Vector2(this.position.x - ball.position.x, this.position.y - ball.position.y);
						ball.velocity = new Vector3(this.velocity.x + controlVector.x, this.velocity.y + controlVector.y, 0);
						this.state = PlayerState.dribbling;
						this.runSpeed = DRIB_PEN;
						this.posTarget = goalTarget.rotate((float) Math.random()*40-20);
						this.runon = 0;
						ball.possession = this.team;
						posPlayer = this;
						ball.curl = 0;
						ball.rotation = 0;
						ball.velocity = new Vector3();
					}
				}
				break;
			}
			if (Math.abs(this.posTarget.x) > 620) this.posTarget.x = 620 * Math.signum(this.posTarget.x);
			if (Math.abs(this.posTarget.y) > 1000) this.posTarget.y = 1000 * Math.signum(this.posTarget.y);
			if (this.kicking <= 0 && this.tackling <= 0 && this.stun <= 0 && this.runon <= 0) {
				if (this.position.dst(this.posTarget) > 10)	this.velocity = this.velocity.lerp(this.posTarget.cpy().sub(this.position).nor().scl(this.dirSpeed * this.runSpeed), 0.05f);
				}
			// ACTIONS TO TAKE EVERY TIME THE COMPUTER PLAYER REASSESSES
			this.thinkCounter -= deltaTime;
			if (this.thinkCounter <= 0) {
				this.thinkCounter = this.thinkTime;
				switch(this.state) {
				case dribbling:
					this.posTarget = goalTarget.rotate((float) Math.random()*20-10);
					ArrayList<DirScore> directions = new ArrayList<DirScore>();
					for (float ang = (float) Math.random() * 22.5f; ang < 360; ang += 22.5) {
						float basescore = 0;
						for (Polar p: theirTeamPolar) {
							float angDiff = angCompare(p.angle, ang);
							if (p.distance < 200) {
								if (angDiff < 75) basescore -= (75 - angDiff) * Math.exp(-p.distance / 200) * 3;
								if (angDiff > 75 & angDiff < 105) basescore += 15 * Math.exp(-p.distance / 200) * 2;
							}
						}
						basescore += (90 - angCompare(ang, new Vector2(ball.position.x, ball.position.y).sub(this.position).angle())) / (Math.exp(Math.max(-this.position.dst(ball.position.x, ball.position.y), -30) / 100));
						basescore += (90 - angCompare(ang, goalTarget.cpy().sub(this.position).angle())) / 2;
						if (offPitch(this.position.cpy().add(new Vector2(150,0).rotate(ang)))) basescore -= 100;
						directions.add(new DirScore(ang, basescore));
					}
					Collections.sort(directions, directions.get(0));
//					System.out.println(directions.get(0).angle + ": " + directions.get(0).score);
//					for (DirScore d: directions) System.out.println(d.angle + ": " + d.score);
//					System.out.println("=================");
					if (directions.get(0).score > 50) {
						this.posTarget = this.position.cpy().add(new Vector2(directions.get(0).score - 50, 0).rotate(directions.get(0).angle));
					}
					else {
						this.posTarget = this.position.cpy();
						this.velocity.scl(0.5f);
					}
					if (this.theirTeamPolar.length > 0 && this.theirTeamPolar[0].distance < 200) {
						Player passTarget = null;
						for (Player p: this.myTeam) {
							int ind = this.myTeam.indexOf(p);
							if (this.myTeamPolar[ind].distance < 600 && this.myTeamPolar[ind].distance > 100 &&
									angCompare(this.myTeamPolar[ind].angle, this.angle) < 90) {
								boolean reject = false;
								for (Player o: this.theirTeam){
									if (between(this, p, o, 15)) {
										reject = true;
									}
								}
								if (!reject) {
									if (passTarget == null || Math.random() < 0.4f) passTarget = p;
								}
							}
						}
						if (passTarget != null && Math.random() > 0.3) {
							Vector2 passDest = passTarget.position.cpy();
							Vector2 passDir = passDest.cpy().sub(ball.position.x, ball.position.y);
							if (Math.abs(passTarget.velocity.angle() - 90 - (180 * passTarget.team)) < 75  &&
									(this.position.y - passTarget.position.y) * (1 - 2 * this.team) > 0) {
								boolean reject = false;
								for (Player o: this.theirTeam) {
									if (!between(this, passDir.cpy().add(passTarget.velocity.cpy().scl(passDir.len()/250)), o, 15)) reject = true;
								}
								if (!reject) {
									passDir.add(passTarget.velocity.cpy().scl(passDir.len()/350));
								}
							}
							this.runSpeed = 1;
							ball.velocity = new Vector3(passDir.x, passDir.y, passDir.len()/100).nor().scl((float) Math.pow(passDir.len(), 0.7f) * 11);
							ball.possession = -1;
							posPlayer = null;
							this.possession = false;
							this.kicking = 0.4f;
						}
//						else {
//							this.posTarget = this.position.cpy().add(new Vector2(100, 0).rotate(theirTeamPolar[0].angle + 90 - (180 * (float) Math.random())));
//						}
					}
					if (this.calledFor != null) {
						if (this.position.dst(this.calledFor.position) < 600 && this.position.dst(this.calledFor.position) > 150 &&
								Math.abs(((this.calledFor.position.cpy().sub(this.position).angle() - this.angle + 180) % 360) - 180) < 120) {
							Vector2 passDest = this.calledFor.position.cpy();
							Vector2 passDir = passDest.cpy().sub(ball.position.x, ball.position.y);
							if (Math.abs(this.calledFor.velocity.angle() - 90 - (180 * this.calledFor.team)) < 75  &&
									(this.position.y - this.calledFor.position.y) * (1 - 2 * this.team) > 0) {
								boolean reject = false;
								for (Player o: this.theirTeam) {
									if (!between(this, passDir.cpy().add(this.calledFor.velocity.cpy().scl(passDir.len()/250)), o, 15)) reject = true;
								}
								if (!reject) {
									passDir.add(this.calledFor.velocity.cpy().scl(passDir.len()/250));
								}
							}
							this.runSpeed = 1;
							ball.velocity = new Vector3(passDir.x, passDir.y, passDir.len()/100).nor().scl((float) Math.pow(passDir.len(), 0.4f) * 70);
							ball.possession = -1;
							posPlayer = null;
							this.possession = false;
							this.kicking = 0.4f;
						}
						this.calledFor = null;
					}
					break;
				case moving:
					if (posPlayer != null) {
						float dir = ((myMod(posPlayer.angle + 270 - (180 * team), 360) - 180) / 2) - 90 + (180 * team);
						Vector2 newTarget = posPlayer.position.cpy().add(new Vector2(250,0).rotate(dir + ((float) Math.random() * 15)));
						this.posTarget = newTarget;
					}
					break;
				case marking:
					break;
				case positioning:
					this.posTarget = this.formation.cpy();
					break;
				case chasing:
					break;
				case stunned:
					break;
				case runningon:
					break;
				}
			}
			this.position.add(this.velocity.cpy().scl(deltaTime));
			if (this.kicking > 0 || this.tackling > 0) this.velocity.scl(0.95f);
			if (this.stun > 0) this.velocity.scl(0.9f);
			this.kicking = Math.max(this.kicking - deltaTime, 0);
			this.tackling = Math.max(this.tackling - deltaTime, 0);
			if (this.runon > 0) {
				this.runon -= deltaTime;
				if (this.runon <= 0) {
					this.runon = 0;
					ball.possession = -1;
					passedTo = null;
					passEnd = null;
				}
			}
			this.stun = Math.max(0, this.stun - deltaTime);
			this.label = Float.toString(this.velocity.len());
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
//			System.out.print(masterTime + " " + this.position + " " + this.velocity.cpy().scl(deltaTime));
			if(deltaTime == 0) return;
			this.roll += Math.sqrt(new Vector2(this.velocity.x, this.velocity.y).len()) * deltaTime * 10;
			if (Math.abs(this.position.y) > 1008  && Math.abs(this.position.y) < 1056 &&
					Math.abs(Math.abs(this.position.x) - 150) < 10) {
				this.velocity.x *= 0.5;
				this.velocity.y *= 0.95;
			}
			if (Math.abs(this.position.x) < 150 && this.position.z < 100) {
				if (Math.abs(this.position.y) < 1016) {
					if (Math.abs(this.position.add(this.velocity.cpy().scl(deltaTime)).y) > 1016) {
						System.out.println("GOAL!!!");
					}
				}
				else if (Math.abs(Math.abs(this.position.y) - 1056) < 10) {
					this.velocity.y *= 0.5;
					this.velocity.x *= 0.95;
					this.position.add(this.velocity.cpy().scl(deltaTime));
				}
				else this.position.add(this.velocity.cpy().scl(deltaTime));
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
				this.velocity.z = (float) Math.random() * 800;
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
			messagea = "Ball Height: " + this.position.z;
//			System.out.println(" " + this.position);
			messageb = "Ball impedence: " + ((this.velocity.len() / this.oldVelocity.len()) - 1) / deltaTime;
		}

		private void render(float deltaTime) {
			TextureRegion frame = null;
			frame = ballAnim.getKeyFrame(ball.roll);
			batch.draw(shadow, this.position.x - Ball.SIZE/2 + this.position.z / 10, this.position.y - Ball.SIZE/2 + this.position.z / 10, Ball.SIZE/2, Ball.SIZE/2, Ball.SIZE, Ball.SIZE, 1, 1, 0);
			batch.draw(frame, this.position.x - Ball.SIZE/2, this.position.y - Ball.SIZE/2, Ball.SIZE/2, Ball.SIZE/2, Ball.SIZE, Ball.SIZE, (1f + this.position.z/250), (1f + this.position.z/250), this.angle + this.rotation);
		}
		
		public ArrayList<Player> getPlayerList(int team) {
			ArrayList<Player> relPlayers = new ArrayList<Player>(5);
			for (User u: users) {
				if (team < 0 || team == u.team) {
					relPlayers.add(u);
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
	}

	private static final float GRAVITY = 500f;
	private static final float CONTROL = 0.7f;
	private static final float GROUND_RESISTANCE = 1.5f;
	private static final float AIR_RESISTANCE = 0.4f;
	private static final float BOUNCINESS = 0.85f;
	private static final float CAM_SPEED = 8f;
	private static final float PL_BOUNCE = 0.6f;
	private static final int CHASERS = 2;
	private static final int SUPPORT = 1;
	private static final float DRIB_PEN = 0.85f;
	private OrthographicCamera camera;
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
	private Array<Animation> playerRuns = new Array<Animation>(true, 16);
	private Array<Animation> playerStopped = new Array<Animation>(true, 16);
	private Array<Animation> playerKicks = new Array<Animation>(true, 16);
	private Animation ballAnim = new Animation(7);
	private static int[] teamSizes = {2, 0};
	private User[] users;
	private ComPlayer[] complayers;
	private Player[] allplayers;
	private Ball ball;
	public List<ArrayList<Player>> ballPlayers;
	private int[][][] spriteAtlas;
	private Rectangle glViewPort;
	private BitmapFont font;
	private Matrix4 normalProjection;
	private String messagea, messageb;
	boolean PAUSED = false;
	private Player posPlayer;
	private ShapeRenderer shapeRenderer;
	Vector2[] posts = {new Vector2(-140, 1008), new Vector2(140, 1008), new Vector2(-140, -1008), new Vector2(140, -1008)};
	Vector2 passEnd = null;
	Player passedTo = null;
	Vector2 passV0 = null;
	Vector2 passStart = null;
	float passElapsed = 0;
	float passAng = 0;
	float masterTime = 0;

	@Override
	public void create() {
		
		shapeRenderer = new ShapeRenderer();
		
		Texture.setEnforcePotImages(false);
		float w = Gdx.graphics.getWidth();
		float h = Gdx.graphics.getHeight();
		glViewPort = new Rectangle(0, 0, w, h);
		font = new BitmapFont(Gdx.files.internal("data/font16.fnt"),
		         Gdx.files.internal("data/font16.png"), false);
		normalProjection = new Matrix4().setToOrtho2D(0, 0, Gdx.graphics.getWidth(),  Gdx.graphics.getHeight());
		messagea = "";
		messageb = "";
		
		camera = new OrthographicCamera(w*1.5f, h*1.5f);
		batch = new SpriteBatch();
		
        users = new User[1];
        users[0] = new User(new Vector2(100, 100), 0, "Richard", new Vector2(100, 100));
		users[0].velocity = new Vector2(0, 0);
		
		complayers = new ComPlayer[teamSizes[0] + teamSizes[1] - 1];
		allplayers = new Player[teamSizes[0] + teamSizes[1]];
		for (int i = 0; i < teamSizes[1]; i++) {
			Vector2 pos = new Vector2(0, -100 - (i * 200));
			complayers[i] = new ComPlayer(pos, 1, "Clive", pos.cpy());
			complayers[i].dirSpeed = 160;
			allplayers[i] = complayers[i];
		}
		for (int i = teamSizes[1]; i < teamSizes[0] + teamSizes[1] - 1; i++) {
			Vector2 pos = new Vector2(0, 100 + (i * 200));
			complayers[i] = new ComPlayer(pos, 0, "Gavin", pos.cpy());
			complayers[i].dirSpeed = 160;
			allplayers[i] = complayers[i];
		}
		allplayers[teamSizes[0] + teamSizes[1] -1] = users[0];
		ballPlayers = new ArrayList<ArrayList<Player>>(3);
		posPlayer = null;
		
		ball = new Ball(new Vector3(0, 0, 0));

		String spriteRaw = Gdx.files.internal("data/spriteatlas.dat").readString();
		spriteAtlas = readArrayFromString(spriteRaw, 8, 9, 2);
		
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
//				System.out.println(i + " " + j + " " + thisEntry.get("x") + thisEntry.get("y"));
			}
			playerRuns.add(new Animation(15f, spriteArray));
			playerRuns.get(i).setPlayMode(Animation.LOOP_PINGPONG);
			playerStopped.add(new Animation(1f, regions[spriteAtlas[i][7][1]][spriteAtlas[i][7][0]]));
			playerStopped.get(i).setPlayMode(Animation.NORMAL);
			playerKicks.add(new Animation(1f, regions[spriteAtlas[i][0][1]][spriteAtlas[i][0][0]]));
			playerKicks.get(i).setPlayMode(Animation.NORMAL);			
		}
		for (int i=0; i<8; i++) {
			Array<TextureRegion> spriteArray = new Array<TextureRegion>(true, 7);
			for (int j=0; j<7; j++) {
				spriteArray.add(regions[spriteAtlas[i][j][1]+5][spriteAtlas[i][j][0]]);
//				System.out.println(i + " " + j + " " + thisEntry.get("x") + thisEntry.get("y"));
			}
			playerRuns.add(new Animation(15f, spriteArray));
			playerRuns.get(i+8).setPlayMode(Animation.LOOP_PINGPONG);
			playerStopped.add(new Animation(1f, regions[spriteAtlas[i][7][1]+5][spriteAtlas[i][7][0]]));
			playerStopped.get(i+8).setPlayMode(Animation.NORMAL);
			playerKicks.add(new Animation(1f, regions[spriteAtlas[i][0][1]+5][spriteAtlas[i][0][0]]));
			playerKicks.get(i+8).setPlayMode(Animation.NORMAL);			
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
			passElapsed += deltaTime;
			masterTime += deltaTime;
			messagea = "Stun : " + users[0].stun;
			messageb = "Tackling : " + users[0].tackling;
			ballPlayers.set(0, ball.getPlayerList(0));
			ballPlayers.set(1, ball.getPlayerList(1));
			ballPlayers.set(2, ball.getPlayerList(-1));
			Player closePlayer = ballPlayers.get(2).get(0);
			// REASSIGN POSSESSION
			if (closePlayer.position.dst(new Vector2(ball.position.x, ball.position.y)) > (closePlayer.SIZE + Ball.SIZE) * 0.8) {
				posPlayer = null;
				clearPossession();
			}
			else if (closePlayer.kicking <= 0 && closePlayer.tackling <= 0 && closePlayer.stun <= 0) {
				ball.possession = closePlayer.team;
				posPlayer = closePlayer;
				closePlayer.possession = true;
			}
			// REASSIGN ROLES
			for (int team = 0; team < 2; team++) {
				ArrayList<Player> thisTeam = ballPlayers.get(team);
				if (ball.possession == team) {
					int supCount = 0;
					for (int i = 0; i < thisTeam.size(); i++) {
						ComPlayer thisPlayer = null;
						for (ComPlayer c: complayers) if (c.equals(thisTeam.get(i))) thisPlayer = c;
						if (thisPlayer == null) continue;
						if (thisPlayer.stun > 0) thisPlayer.state = PlayerState.stunned;
						else if (thisPlayer.runon > 0) thisPlayer.state = PlayerState.runningon;
						else if (thisPlayer.possession) {
							thisPlayer.state = PlayerState.dribbling;
							thisPlayer.runSpeed = DRIB_PEN;
						}
						else if (i == 0) thisPlayer.state = PlayerState.chasing;
						else if (supCount < SUPPORT) {
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
						for (ComPlayer c: complayers) if (c.equals(thisTeam.get(i))) thisPlayer = c;
						if (thisPlayer == null) continue;
						if (thisPlayer.stun > 0) thisPlayer.state = PlayerState.stunned;
						else if (chaseCount < CHASERS) {
							thisPlayer.state = PlayerState.chasing;
							chaseCount += 1;
						}
						else thisPlayer.state = PlayerState.positioning;
					}
				}
			}
			for (User u: users) {
				u.recalcPlayer(deltaTime);
			}
			for (ComPlayer c: complayers) {
				if (c != null) {
					c.recalcPlayer(deltaTime);
				}
			}
			for (User u: users) {
				u.updatePlayer(deltaTime);
			}
			for (ComPlayer c: complayers) {
				if (c != null) {
					c.updatePlayer(deltaTime);
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
			for (User u: users) {
				u.renderPlayer(deltaTime);
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
			shapeRenderer.end();
		}
		else {
			if (Gdx.input.isKeyPressed(Keys.I)) PAUSED = false;
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

	private void collisionDetect(float deltaTime) {
		for (int i=0; i<allplayers.length-1; i++) {
			for (int j=i+1; j<allplayers.length; j++) {
				if (allplayers[i].tackling + allplayers[j].tackling <= 0) {
					if (allplayers[i].position.dst(allplayers[j].position) < allplayers[i].SIZE * 0.7f) {
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
				System.out.println("POST!!!");
				float Pang = new Vector2(post.x - ball.position.x, post.y - ball.position.y).angle();
				float beta = new Vector2(ball.velocity.x, ball.velocity.y).angle();
				ball.velocity.rotate((2 * Pang) - (2 * beta) - 180, 0, 0, 1);
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

	public Vector2 vecReject(Vector2 a, Vector2 b) {
		Vector2 bhat = b.cpy().nor();
		float theta = b.angle() - a.angle();
		return a.sub(bhat.scl(a.len() * (float) Math.cos(theta)));
	}
	
	public void clearPossession() {
		for (ComPlayer c: complayers) {
			if (c.possession) {
				c.possession = false;
//				if (c.tackling <= 0) c.stun = 1.1f;
			}
		}
		for (User u: users) {
			if (u.possession) { 
				u.possession = false;
//				if (u.tackling <= 0) u.stun = 1.1f;
			}
		}
	}

	public float angCompare(float a, float b) {
		return (float) Math.abs(((a - b + 180) % 360) - 180);
	}

	public boolean offPitch(Vector2 p) {
		if (Math.abs(p.x) > 620) return true;
		if (Math.abs(p.y) > 1004) return true;
		return false;
	}
	
	public boolean between(Player a, Player b, Player c, float angle) {
		if (a.position.dst(b.position) < a.position.dst(c.position)) return false;
		if (Math.abs(((b.position.cpy().sub(a.position).angle() - c.position.cpy().sub(a.position).angle() + 180) % 360) - 180) < angle) return false;
		return true;
	}
	
	public boolean between(Player a, Ball b, Player c, float angle) {
		if (a.position.dst(new Vector2(b.position.x, b.position.y)) < a.position.dst(c.position)) return false;
		if (Math.abs(((new Vector2(b.position.x, b.position.y).sub(a.position).angle() - c.position.cpy().sub(a.position).angle() + 180) % 360) - 180) < angle) return false;
		return true;		
	}
	
	public boolean between(Player a, Vector2 b, Player c, float angle) {
		if (a.position.dst(new Vector2(b.x, b.y)) < a.position.dst(c.position)) return false;
		if (Math.abs(((new Vector2(b.x, b.y).sub(a.position).angle() - c.position.cpy().sub(a.position).angle() + 180) % 360) - 180) < angle) return false;
		return true;		
	}	
	
	public void infoDump() {
		for (User u: users) {
			System.out.println("Name:" + u.name + ", Team: " + u.team + ", Pos: " + u.position + ", Vel: " + u.velocity + ", Angle: " + u.angle);
		}
		for (ComPlayer c: complayers) {
			System.out.println("Name:" + c.name + ", Team: " + c.team + ", Pos: " + c.position + ", Vel: " + c.velocity + ", Angle: " + c.angle + "State: " + c.state + ", Stun: " + c.stun + ", Kicking: " + c.kicking + ", Tackling: " + c.tackling);
			if (c.state == PlayerState.chasing) System.out.println("Ball pos : " + ball.position + ", Target: " + c.posTarget);
			if (c.equals(posPlayer)) System.out.println("Has possession");
		}
		System.out.println("Possession: " + ball.possession);
	}
	
}