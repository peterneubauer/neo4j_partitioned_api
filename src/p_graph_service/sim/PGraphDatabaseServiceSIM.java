package p_graph_service.sim;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.event.KernelEventHandler;
import org.neo4j.graphdb.event.TransactionEventHandler;
import org.neo4j.kernel.EmbeddedGraphDatabase;

import p_graph_service.PGraphDatabaseService;
import p_graph_service.PlacementPolicy;
import p_graph_service.core.InstanceInfo;
import p_graph_service.core.InstanceInfo.InfoKey;
import p_graph_service.policy.RandomPlacement;

public class PGraphDatabaseServiceSIM implements PGraphDatabaseService {
	public final static String col = "_color";
	
	private final long SERVICE_ID;
	private GraphDatabaseService db;
	private PlacementPolicy placementPol;
	protected long VERS;
	// neo4j instances
	public HashMap<Byte, InstanceInfo>INST;
	// db folder
	protected File DB_DIR;
	
	
	@SuppressWarnings("unchecked")
	public PGraphDatabaseServiceSIM(String folder, long instID) {
		this.db=new EmbeddedGraphDatabase(folder);
		this.SERVICE_ID = instID;
		
		this.VERS = 0;
		this.INST = new HashMap<Byte, InstanceInfo>();
		this.DB_DIR = new File(folder);
		this.placementPol = new RandomPlacement();
		
		// load stored meta information
		try {
			InputStream fips = new FileInputStream(new File(DB_DIR.getName()+"/info"));
			ObjectInputStream oips = new ObjectInputStream(fips);
			this.INST = (HashMap<Byte, InstanceInfo>) oips.readObject();
		
			oips.close();
			fips.close();	
		} catch (Exception e) {
			Transaction tx = beginTx();
			try {
				for(Node n :  getAllNodes()){
					if(n.getId() == 0)continue;
					
					byte pos = (Byte)n.getProperty(col);
					if(!INST.containsKey(pos)){
						InstanceInfo inf = new  InstanceInfo();
						inf.log(InfoKey.n_create);
						inf.resetTraffic();
						INST.put(pos, inf);
					}else{
						InstanceInfo inf = INST.get(pos);
						inf.log(InfoKey.n_create);
						inf.resetTraffic();
						INST.put(pos, inf);
					}
				}
				tx.success();
			} finally {
				tx.finish();
			}
		}
		
		for(byte b : INST.keySet()){
			placementPol.addInstance(b, INST.get(b));
		}
		
	}
	
	
	@Override
	public boolean addInstance() {
		byte high =0;
		for(byte b: INST.keySet()){
			if(high <= b)high++;
		}
		INST.put(high, new InstanceInfo());
		return true;
	}

	@Override
	public boolean addInstance(long id) {
		INST.put((byte)id, new InstanceInfo());
		return true;
	}

	@Override
	public Node createNode(long GID) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Node createNodeOn(long instanceID) {
		byte byteID = (byte) instanceID;
		if(INST.containsKey(byteID)){
			INST.get(byteID).log(InfoKey.n_create);
			Node n =db.createNode();
			n.setProperty(col, byteID);
			return n;
		}
		return null;
	}

	@Override
	public Node createNodeOn(long GID, long instanceID) {
		throw new UnsupportedOperationException();
	}

	@Override
	public InstanceInfo getInstanceInfoFor(long id) {
		return INST.get((byte)id);
	}

	@Override
	public long[] getInstancesIDs() {
		long[] res = new long[INST.keySet().size()];
		int i=0;
		for(byte k : INST.keySet()){
			res[i] = (long)k;
			i++;
		}
		return res;
	}

	@Override
	public int getNumInstances() {
		return INST.size();
	}

	@Override
	public PlacementPolicy getPlacementPolicy() {
		return placementPol;
	}

	@Override
	public long getServiceID() {
		return SERVICE_ID;
	}

	@Override
	public boolean migrateInstance(String path, long id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void moveNodes(Iterable<Node> nodes, long instanceID) {
		byte byteID = (byte) instanceID;
		if(INST.containsKey(byteID)){
			InstanceInfo inf = INST.get(byteID);
			for(Node n: nodes){
				byte curPos = (Byte) n.getProperty(col);
				INST.get(curPos).log(InfoKey.n_delete);
				n.setProperty(col, byteID);
				inf.log(InfoKey.n_create);	
			}
			VERS++;
		}		
	}

	@Override
	public boolean removeInstance(long id) {
		byte byteID = (byte) id;
		if(INST.containsKey(id)){
			if(INST.get(byteID).getValue(InfoKey.NumNodes) == 0){
				INST.remove(byteID);
				return true;
			}
		}
		return false;
	}

	@Override
	public void resetLogging() {
		for(InstanceInfo inf: INST.values()){
			inf.resetTraffic();
		}
	}

	@Override
	public void resetLoggingOn(long id) {
		byte byteID = (byte) id;
		if(INST.containsKey(id)){
			INST.get(byteID).resetTraffic();
		}
	}

	@Override
	public void setPlacementPolicy(PlacementPolicy pol) {
		this.placementPol = pol;
	}

	@Override
	public Transaction beginTx() {
		return db.beginTx();
	}

	@Override
	public Node createNode() {
		return createNodeOn(placementPol.getPosition());
	}

	@Override
	public boolean enableRemoteShell() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean enableRemoteShell(Map<String, Serializable> initialProperties) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterable<Node> getAllNodes() {
		return db.getAllNodes();
	}

	@Override
	public Node getNodeById(long id) {
		Node n = db.getNodeById(id);
		byte pos = (Byte) n.getProperty(col);
		INST.get(pos).log(InfoKey.Traffic);
		return n;
	}

	@Override
	public Node getReferenceNode() {
		Node n = db.getNodeById(0);
		byte pos = (Byte) n.getProperty(col);
		INST.get(pos).log(InfoKey.Traffic);
		return n;
	}

	@Override
	public Relationship getRelationshipById(long id) {
		Relationship rs = db.getRelationshipById(id);
		byte pos = (Byte) rs.getStartNode().getProperty(col);
		INST.get(pos).log(InfoKey.Traffic);
		return rs;
	}

	@Override
	public Iterable<RelationshipType> getRelationshipTypes() {
		return db.getRelationshipTypes();
	}

	@Override
	public KernelEventHandler registerKernelEventHandler(
			KernelEventHandler handler) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> TransactionEventHandler<T> registerTransactionEventHandler(
			TransactionEventHandler<T> handler) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void shutdown() {
		// store meta information
		try{
			OutputStream fops = new FileOutputStream(new File(DB_DIR.getName()+"/info"));
			ObjectOutputStream oops = new ObjectOutputStream(fops);
			oops.writeObject(INST);
			oops.close();
			fops.close();
		}
		catch (Exception e) {
			// nothing to do there
		}
		db.shutdown();
	}

	@Override
	public KernelEventHandler unregisterKernelEventHandler(
			KernelEventHandler handler) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> TransactionEventHandler<T> unregisterTransactionEventHandler(
			TransactionEventHandler<T> handler) {
		throw new UnsupportedOperationException();
	}

}