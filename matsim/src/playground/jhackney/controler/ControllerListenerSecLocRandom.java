/* *********************************************************************** *
 * project: org.matsim.*
 * ControlerListenerSecLocRandom.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package playground.jhackney.controler;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.matsim.controler.Controler;
import org.matsim.controler.events.IterationEndsEvent;
import org.matsim.controler.events.IterationStartsEvent;
import org.matsim.controler.events.StartupEvent;
import org.matsim.controler.listener.IterationEndsListener;
import org.matsim.controler.listener.IterationStartsListener;
import org.matsim.controler.listener.StartupListener;
import org.matsim.facilities.Facilities;
import org.matsim.gbl.Gbl;
import org.matsim.plans.Knowledge;
import org.matsim.plans.Person;
import org.matsim.plans.Plan;
import org.matsim.plans.Plans;
import org.matsim.router.PlansCalcRoute;
import org.matsim.socialnetworks.algorithms.PersonSNSecLocRandomReRoute;
import org.matsim.socialnetworks.interactions.NonSpatialInteractor;
import org.matsim.socialnetworks.interactions.SocialAct;
import org.matsim.socialnetworks.interactions.SpatialInteractor;
import org.matsim.socialnetworks.interactions.SpatialSocialOpportunityTracker;
import org.matsim.socialnetworks.io.PajekWriter;
import org.matsim.socialnetworks.replanning.SNSecLocRandom;
import org.matsim.socialnetworks.socialnet.SocialNetwork;
import org.matsim.socialnetworks.statistics.SocialNetworkStatistics;
import org.matsim.world.algorithms.WorldBottom2TopCompletion;

import playground.jhackney.algorithms.PlanRandomReplaceSecLoc;


/**
 * This controler implements a plan algorithm in which the locations of
 * secondary activities are changed randomly, assuming the agents know
 * everything about the world.
 * 
 * The difference between this controller and the SNControllerSecLoc is the call
 * to the PlanRandomReplaceSecLoc instead of to PersonSNSecLocRandomReRoute algorithm.
 * The former replaces the facility from the facilities layer while the latter
 * replaces it from the agent's knowledge.
 * 
 * Thus in this conroller the information
 * exchange between agents does not matter in replanning. There is no correlation between
 * the information used by agents and that of their alters.
 * 
 * The intent is to be able to compare a random secondary location choice
 * algorithm with one that uses social network exchanges of information
 *  
 * @author jhackney
 *
 */
public class ControllerListenerSecLocRandom implements StartupListener, IterationStartsListener, IterationEndsListener {

	private boolean CALCSTATS = true;
	private static final String DIRECTORY_SN = "socialnets/";
	public static String SOCNET_OUT_DIR = null;
	private boolean createGraphs = true;

	SocialNetwork snet;
	SocialNetworkStatistics snetstat;
	PajekWriter pjw;
	NonSpatialInteractor plansInteractorNS;//non-spatial (not observed, ICT)
	SpatialInteractor plansInteractorS;//spatial (face to face)
	int max_sn_iter;
	int snIter;
	String [] infoToExchange;//type of info for non-spatial exchange is read in
	public static String activityTypesForEncounters[]={"home","work","shop","education","leisure"};

	SpatialSocialOpportunityTracker gen2 = new SpatialSocialOpportunityTracker();
	Collection<SocialAct> socialPlans=null;

	private final Logger log = Logger.getLogger(ControllerListenerSecLocRandom.class);

//	Variables for allocating the spatial meetings among different types of activities
	double fractionS[];
	HashMap<String,Double> rndEncounterProbs= new HashMap<String,Double>();
//	New variables for replanning
	int replan_interval;

	private Controler controler = null;

	public void notifyStartup(final StartupEvent event) {
		this.controler = event.getControler();

		/* code previously in loadData() */

		// Make a new zone layer (Raster)
//		if(!(this.controler.getConfig().socnetmodule().getGridSpace().equals(null))){
//		int gridSpacing = Integer.valueOf(this.controler.getConfig().socnetmodule().getGridSpace());
//		if(Gbl.getWorld().getLayers().size()>2){
//		System.out.println("World already contains a zone layer");
//		new WorldCreateRasterLayer(gridSpacing).run(Gbl.getWorld());
//		}else{
//		new WorldCreateRasterLayer2(gridSpacing).run(Gbl.getWorld());
////		new WorldCreateRasterLayer(3000).run(Gbl.getWorld());
//		}
//		// Stitch together the world
//		new WorldBottom2TopCompletion().run(Gbl.getWorld());
//		}
//		int gridSpacing = Integer.valueOf(this.controler.getConfig().socnetmodule().getGridSpace());
//		new WorldCreateRasterLayer2(gridSpacing).run(Gbl.getWorld());

		// Complete the world to make sure that the layers all have relevant mapping rules
		new WorldBottom2TopCompletion().run(Gbl.getWorld());
		//loadSocialNetwork();
		// if (this.config.socialnet().getInputFile() == null) {
		//this.log.info("Loading initial social network");
		// also initializes knowledge.map.egonet
		//}

		this.log.info(" Initializing agent knowledge about geography ...");
		this.log.info(" If scoring crashes later it may be because this only initializes knowledge for the selected plan. This will happen if there are more than one initial plan");
		initializeKnowledge(this.controler.getPopulation());
		this.log.info("... done");

		this.log.info(" Setting boolean to create graphical output");
		this.controler.setCreateGraphs(createGraphs);
		this.log.info("... done");
		/* code previously in startup() */
		snsetup();
	}

	public void notifyIterationEnds(final IterationEndsEvent event) {
		/* code previously in finishIteration() */
		this.log.info("finishIteration ... ");
//		snIter = event.getIteration();

		if( event.getIteration()%replan_interval==0){
			// Removing the social links here rather than before the replanning and assignment lets you use the actual encounters in a social score
			this.log.info(" Removing social links ...");
			this.snet.removeLinks(snIter);
			this.log.info(" ... done");

//			You could forget activities here, after the replanning and assignment

			if(CALCSTATS){
				this.log.info(" Calculating and reporting network statistics ...");
				this.snetstat.calculate(snIter, this.snet, this.controler.getPopulation());
				this.log.info(" ... done");
			}

			this.log.info(" Writing out social network for iteration " + snIter + " ...");
			this.pjw.write(this.snet.getLinks(), this.controler.getPopulation(), snIter);
			this.pjw.writeGeo(this.controler.getPopulation(), this.snet, snIter);
			this.log.info(" ... done");
		}
		if (event.getIteration() == this.controler.getLastIteration()) {
			if(CALCSTATS){
				this.log.info("----------Closing social network statistic files and wrapping up ---------------");
				this.snetstat.closeFiles();
			}
		}
	}

	public void notifyIterationStarts(final IterationStartsEvent event) {
		Controler controler = event.getControler();

		/* code previously in setupIteration() */
//		int snIter = event.getIteration();

		if( event.getIteration()%replan_interval==0 && event.getIteration()!=this.controler.getFirstIteration()){
//			if( event.getIteration()%replan_interval==0){

//			// add the socNet-score to the existing scoring function
//			controler.setScoringFunctionFactory(
//			new SNScoringFunctionFactory03(controler.getScoringFunctionFactory()));

			if (total_spatial_fraction(this.fractionS) > 0) { // only generate the map if spatial meeting is important in this experiment

				//if(Events have just changed or if no events are yet available and if there is an interest in the planned interactions)
				this.log.info("  Generating planned [Spatial] socializing opportunities ...");
				this.log.info("   Makes a map of time/place windows for all planned encounters between the agents");
				this.socialPlans = this.gen2.generateValues(this.controler.getPopulation());
				this.log.info("...finished.");

				//}// end if

				// Agents' planned interactions
				this.log.info("  Agents planned social interactions ...");
				this.log.info("  Agents' relationships are updated to reflect these interactions! ...");
				this.plansInteractorS.interact(this.socialPlans, this.rndEncounterProbs, snIter);

			}else{
				this.log.info("     (none)");
			}
			this.log.info(" ... Spatial interactions done\n");

			this.log.info(" Non-Spatial interactions ...");
			for (int ii = 0; ii < this.infoToExchange.length; ii++) {
				String facTypeNS = this.infoToExchange[ii];

				//	Geographic Knowledge about all types of places is exchanged
				if (!facTypeNS.equals("none")) {
					this.log.info("  Geographic Knowledge about all types of places is being exchanged ...");
					this.plansInteractorNS.exchangeGeographicKnowledge(facTypeNS, snIter);
				}
			}

			// Exchange of knowledge about people
			double fract_intro=Double.parseDouble(this.controler.getConfig().socnetmodule().getTriangles());
			if (fract_intro > 0) {
				this.log.info("  Knowledge about other people is being exchanged ...");
				this.plansInteractorNS.exchangeSocialNetKnowledge(snIter);
			}

			this.log.info("  ... done");

			this.log.info(" Forgetting excess activities (locations) OR SHOULD THIS HAPPEN EACH TIME AN ACTIVITY IS LEARNED ...");
			this.log.info("  Should be an algorithm");
			Collection<Person> personList = this.controler.getPopulation().getPersons().values();
			Iterator<Person> iperson = personList.iterator();
			while (iperson.hasNext()) {
				Person p = (Person) iperson.next();
//				Remember a number of activities equal to at least the number of
//				acts per plan times the number of plans in memory
				int max_memory = (int) (p.getSelectedPlan().getActsLegs().size()/2*p.getPlans().size()*1.5);
				p.getKnowledge().map.manageMemory(max_memory, p.getPlans());
			}
			this.log.info(" ... done");

			this.log.info(" ### HERE MODIFY THE PLANS WITH NEW KNOWLEDGE. Make this a person algorithm");
			Iterator<Person> itreplan = this.controler.getPopulation().getPersons().values().iterator();
			while (itreplan.hasNext()) {
				Plan p = (Plan) itreplan.next().getSelectedPlan();
//				new PersonSNSecLocRandomReRoute(activityTypesForEncounters, controler.getNetwork(), controler.getTravelCostCalculator(), controler.getTravelTimeCalculator()).run(p);
				new PlanRandomReplaceSecLoc(activityTypesForEncounters, controler.getNetwork(), controler.getTravelCostCalculator(), controler.getTravelTimeCalculator()).run(p);
//				System.out.println( "SNControler1 Number of plans for person "+p.getPerson().getId()+" "+p.getPerson().getPlans().size());
			}
			this.log.info(" ... done");
			snIter++;
		}
	}

	/* ===================================================================
	 * private methods
	 * =================================================================== */

	void initializeKnowledge(final Plans plans ) {

		// Knowledge is already initialized in some plans files
		// Map agents' knowledge (Activities) to their experience in the plans (Acts)

		for( Person person : plans.getPersons().values() ){

			Knowledge k = person.getKnowledge();
			if(k ==null){
				k = person.createKnowledge("created by " + this.getClass().getName());
			}
			// Initialize knowledge to the facilities that are in all initial plans
//			Iterator<Plan> piter=person.getPlans().iterator();
//			while (piter.hasNext()){
//			Plan plan = piter.next();
			Plan plan = person.getSelectedPlan();
			k.map.initializeActActivityMap(plan);
//			}
		}
	}

	private void snsetup() {

//		Config config = Gbl.getConfig();

		SOCNET_OUT_DIR = Controler.getOutputFilename(DIRECTORY_SN);
		File snDir = new File(SOCNET_OUT_DIR);
		if (!snDir.mkdir() && !snDir.exists()) {
			Gbl.errorMsg("The iterations directory " + SOCNET_OUT_DIR + " could not be created.");
		}

		this.max_sn_iter = Integer.parseInt(this.controler.getConfig().socnetmodule().getNumIterations());
		this.replan_interval = Integer.parseInt(this.controler.getConfig().socnetmodule().getRPInt());
		String rndEncounterProbString = this.controler.getConfig().socnetmodule().getFacWt();
		String xchangeInfoString = this.controler.getConfig().socnetmodule().getXchange();
		this.infoToExchange = getFacTypes(xchangeInfoString);
		this.fractionS = toNumber(rndEncounterProbString);
		this.rndEncounterProbs = mapActivityWeights(activityTypesForEncounters, rndEncounterProbString);

		this.log.info(" Instantiating the Pajek writer ...");
		this.pjw = new PajekWriter(SOCNET_OUT_DIR, (Facilities)Gbl.getWorld().getLayer(Facilities.LAYER_TYPE));
		this.log.info("... done");

		this.log.info(" Initializing the social network ...");
		this.snet = new SocialNetwork(this.controler.getPopulation());
		this.log.info("... done");

		if(CALCSTATS){
//			this.log.info(" Calculating the statistics of the initial social network)...");
			this.log.info(" Opening the files for the social network statistics...");
			this.snetstat=new SocialNetworkStatistics(SOCNET_OUT_DIR);
			this.snetstat.openFiles();
//			Social networks do not change until the first iteration of Replanning,
//			so we can skip writing out this initial state because the networks will still be unchanged after the first assignment
//			this.snetstat.calculate(0, this.snet, this.controler.getPopulation());
			this.log.info(" ... done");
		}

		this.log.info(" Writing out the initial social network ...");
		this.pjw.write(this.snet.getLinks(), this.controler.getPopulation(), this.controler.getFirstIteration());
		this.log.info("... done");

		this.log.info(" Setting up the NonSpatial interactor ...");
		this.plansInteractorNS=new NonSpatialInteractor(this.snet);
		this.log.info("... done");

		this.log.info(" Setting up the Spatial interactor ...");
		this.plansInteractorS=new SpatialInteractor(this.snet);
		this.log.info("... done");

		this.snIter = this.controler.getFirstIteration();
	}

	/**
	 * A method for decyphering the config codes. Part of configuration
	 * reader. Replace eventually with a routine that runs all of the
	 * facTypes but uses a probability for each one, summing to 1.0. Change
	 * the interactors accordingly.
	 *
	 * @param longString
	 * @return
	 */
	private String[] getFacTypes(final String longString) {
		String patternStr = ",";
		String[] s;
		log.info(this.getClass()+ "getFacTypes:	!!add keyword\"any\" and a new interact method to exchange info of any factility types (compatible with probabilities)");
		if (longString.equals("all-p")) {
			s = new String[5];
			s[0] = "home";
			s[1] = "work";
			s[2] = "education";
			s[3] = "leisure";
			s[4] = "shop";
		} else if (longString.equals("all+p")) {
			s = new String[6];
			s[0] = "home";
			s[1] = "work";
			s[3] = "education";
			s[4] = "leisure";
			s[5] = "shop";
			s[6] = "person";
		} else {
			s = longString.split(patternStr);
		}
		for (int i = 0; i < s.length; i++) {
			// if(s[i]!="home"&&s[i]!="work"&&s[i]!="education"&&s[i]!="leisure"&&s[i]!="shop"&&s[i]!="person"&&s[i]!="none"){
			if (!s[i].equals("home") && !s[i].equals("work") && !s[i].equals("education") && !s[i].equals("leisure")
					&& !s[i].equals("shop") && !s[i].equals("person") && !s[i].equals("none")) {
				this.log.info(this.getClass() + ":" + s[i]);
				Gbl.errorMsg("Error on type of info to exchange. Check config file. Use commas with no spaces");
			}
		}
		return s;
	}

	private double[] toNumber(final String longString) {
		String patternStr = ",";
		String[] s;
		s = longString.split(patternStr);
		double[] w = new double[s.length];
		double sum = 0.;
		for (int i = 0; i < s.length; i++) {
			w[i] = Double.valueOf(s[i]).doubleValue();
			if(w[i]<0.||w[i]>1.){
				Gbl.errorMsg("All parameters \"s_weights\" must be >0 and <1. Check config file.");
			}
			sum=sum+w[i];
		}
		if(s.length!=5){
			Gbl.errorMsg("Number of weights for spatial interactions must equal number of facility types. Check config.");
		}
		if(sum<0){
			Gbl.errorMsg("At least one weight for the type of information exchange or meeting place must be > 0, check config file.");
		}
		return w;
	}
	private HashMap<String,Double> mapActivityWeights(final String[] types, final String longString) {
		String patternStr = ",";
		String[] s;
		HashMap<String,Double> map = new HashMap<String,Double>();
		s = longString.split(patternStr);
		double[] w = new double[s.length];
		double sum = 0.;
		for (int i = 0; i < s.length; i++) {
			w[i] = Double.valueOf(s[i]).doubleValue();
			if(w[i]<0.||w[i]>1.){
				Gbl.errorMsg("All parameters \"s_weights\" must be >0 and <1. Check config file.");
			}
			sum=sum+w[i];
			map.put(types[i],w[i]);
		}
		if(s.length!=5){
			Gbl.errorMsg("Number of weights for spatial interactions must equal number of facility types. Check config.");
		}
		if(sum<0){
			Gbl.errorMsg("At least one weight for the type of information exchange or meeting place must be > 0, check config file.");
		}
		return map;
	}

	private double total_spatial_fraction(final double[] fractionS2) {
//		See if we use spatial interaction at all: sum of these must > 0 or else no spatial
//		interactions take place
		double total_spatial_fraction=0;
		for (int jjj = 0; jjj < fractionS2.length; jjj++) {
			total_spatial_fraction = total_spatial_fraction + fractionS2[jjj];
		}
		return total_spatial_fraction;
	}

//	private final void makeSNIterationPath(final int iteration) {
//	new File(getSNIterationPath(iteration)).mkdir();
//	}
//	private final void makeSNIterationPath(final int iteration, final int snIter) {
//	this.log.info(getSNIterationPath(iteration, snIter));
//	File iterationOutFile= new File(getSNIterationPath(iteration, snIter));
//	iterationOutFile.mkdir();
////	if (!iterationOutFile.mkdir()) {
////	Gbl.errorMsg("The output directory " + iterationOutFile + " could not be created. Does its parent directory exist?");
////	}
//	}

	/**
	 * returns the path to the specified social network iteration directory. The directory path does not include the trailing '/'
	 * @param snIter the iteration the path to should be returned
	 * @return path to the specified iteration directory
	 */
	public final static String getSNIterationPath(final int snIter) {
		return Controler.getOutputFilename("ITERS/" + snIter);
	}

	/**
	 * returns the path to the specified iteration directory,
	 * including social network iteration. The directory path does not include the trailing '/'
	 * @param iteration the iteration the path to should be returned
	 * @param snIter
	 * @return path to the specified iteration directory
	 */
	public final static String getSNIterationPath(final int iteration, final int snIter) {
		return Controler.getOutputFilename("ITERS/" + snIter + "/it." + iteration);
	}

}

