package ai.vital.agent.rest.webapp

import org.slf4j.Logger
import ai.vital.vitalservice.VitalService
import ai.vital.vitalsigns.model.VitalApp
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.lang.groovy.GroovyVerticle
import java.util.List
import ai.vital.query.querybuilder.VitalBuilder
import ai.vital.service.vertx3.binary.ResponseMessage
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalservice.query.VitalSelectQuery
import ai.vital.vitalsigns.VitalSigns
import ai.vital.vitalsigns.block.CompactStringSerializer
import ai.vital.vitalsigns.json.JSONSerializer
import ai.vital.vitalsigns.model.GraphMatch
import ai.vital.vitalsigns.model.GraphObject
import ai.vital.vitalsigns.model.VitalSegment
import ai.vital.vitalsigns.model.VitalServiceKey
import ai.vital.vitalsigns.model.property.IProperty
import ai.vital.vitalsigns.model.property.StringProperty
import ai.vital.vitalsigns.uri.URIGenerator
import java.util.Map.Entry
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import java.util.regex.Pattern
import io.vertx.core.Handler
import ai.haley.api.HaleyAPI
import ai.haley.api.HaleyAPIDomainsValidation
import ai.haley.api.session.HaleySession
import ai.haley.api.session.HaleyStatus
import ai.haley.kg.domain.KGAgentSubmission
import ai.vital.domain.Account
import ai.vital.domain.Edge_hasUserLogin
import ai.vital.domain.Login
import ai.vital.domain.Login_PropertiesHelper
// import ai.vital.vitalservice.query.VitalGraphQuery
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.lang.Closure
import groovy.util.logging.Log
import com.google.common.collect.EvictingQueue
import com.vitalai.aimp.domain.AIMPMessage
import com.vitalai.aimp.domain.Channel
import com.vitalai.aimp.domain.MetaQLResultsMessage
import com.vitalai.aimp.domain.UserCommandMessage
import io.vertx.core.eventbus.Message
import java.io.File
import org.slf4j.LoggerFactory
import ai.vital.vitalservice.factory.VitalServiceFactory
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.apache.http.client.CredentialsProvider
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.auth.AuthScope
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
// import org.apache.http.entity.mime.MultipartEntityBuilder

class AgentShopVerticle extends GroovyVerticle {
	
	private final static Logger log = LoggerFactory.getLogger(AgentShopVerticle.class)
	
	private static final AtomicInteger counter = new AtomicInteger()
	
	private int instanceId

	public static boolean initialized = false
	
	public static String appID = null
	
	public static VitalApp app = null

	static Vertx vertxInstance
		
	public static VitalService service

	@Override
	public void start(Future<Void> startFuture)  throws Exception {
		
		log.info("starting...")
		
			
		if(initialized) {
			
			startFuture.complete(true)
			
			return		
		}
		
		instanceId = counter.incrementAndGet()
		
		vertxInstance = vertx
		
		Map<String, Object> mainConfig = vertxInstance.getOrCreateContext().config()
				
		////////////////////////////
		
		String _appID = mainConfig.get('appID')
		
		appID = _appID
		
		if(!_appID) throw new RuntimeException("No 'appID' param")
		
		log.info("appID: ${_appID}")
		
		app = VitalApp.withId( appID )
				
		initialize(startFuture)
			
		startFuture.complete(true)
	}
	
	public void initialize(Future<Void> startFuture) {
		
		VitalService vitalService = null
		
		AgentSubmissionImplHandler agentSubmissionHandler = new AgentSubmissionImplHandler(instanceId)
		
		vertxInstance.eventBus().consumer("agentsubmission") { Message msg ->
			
			agentSubmissionHandler.handle(msg)
		}
		
		AgentQueryImplHandler agentQueryHandler = new AgentQueryImplHandler(instanceId)
		
		vertxInstance.eventBus().consumer("agentquery") { Message msg ->
			
			agentQueryHandler.handle(msg)
		}
		
		VitalSigns vs = VitalSigns.get()
		
		VitalApp app = new VitalApp()
		
		app.URI = "http://vital.ai/ontology/app/123"
		app.appID = "chat-saas"
			
		VitalServiceKey serviceKey = new VitalServiceKey().generateURI(app)
		serviceKey.key = "aaaa-aaaa-aaaa"
					
		service = VitalServiceFactory.openService( serviceKey, "agentshopdb")
		
		log.info("initialized.")
			
		initialized = true
	
	}
	
	public void tearDown(Future<Void> stopFuture) {
		
		initialized = false
	}

	@Override
	public void stop(Future<Void> stopFuture) throws Exception {

		tearDown(stopFuture)
		
		stopFuture.complete(true)
	}
	

	
}
