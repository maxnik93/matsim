/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.core.mobsim.jdeqsim;

import org.matsim.core.events.AgentWait2LinkEventImpl;
import org.matsim.core.events.BasicEventImpl;
import org.matsim.core.events.LinkEnterEventImpl;

/**
 * The micro-simulation internal handler for entering a road.
 *
 * @author rashid_waraich
 */
public class EnterRoadMessage extends EventMessage {

	@Override
	public void handleMessage() {
		// enter the next road
		Road road = Road.getRoad(vehicle.getCurrentLink().getId().toString());
		road.enterRoad(vehicle, getMessageArrivalTime());
	}

	public EnterRoadMessage(Scheduler scheduler, Vehicle vehicle) {
		super(scheduler, vehicle);
		priority = SimulationParameters.PRIORITY_ENTER_ROAD_MESSAGE;
	}

	@Override
	public void processEvent() {
		BasicEventImpl event = null;

		// the first EnterLink in a leg is a Wait2LinkEvent
		if (vehicle.getLinkIndex() == -1) {
			event = new AgentWait2LinkEventImpl(this.getMessageArrivalTime(), vehicle.getOwnerPerson(), vehicle.getCurrentLink(), vehicle.getCurrentLeg());
		} else {
			event = new LinkEnterEventImpl(this.getMessageArrivalTime(), vehicle.getOwnerPerson(), vehicle.getCurrentLink());
		}
		SimulationParameters.getProcessEventThread().processEvent(event);
	}

}
