package ai.vital.agent.rest.webapp

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpClientResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ai.vital.domain.Account
import ai.vital.vitalsigns.json.JSONSerializer
import ai.vital.vitalsigns.model.DomainModel
import ai.vital.vitalsigns.model.GraphObject
import ai.vital.vitalsigns.model.VitalApp
import com.vitalai.haley.domain.HaleyAccount

class AgentShopWebappVerticle extends AbstractVerticle {

	public static boolean initialized = false
	
	private final static Logger log = LoggerFactory.getLogger(AgentShopWebappVerticle.class)
	
	public static VitalApp app

	public static String appVersion
	
	public static String appUnavailableURL
		
	public static String appID

	public static String pathPrefix
	
	public static String sessionDomain
	
	public static String cookiePrefix
	
	public static Boolean cookieSecure
	
	public static String agentshopRestAPI
	
	// async start with notification
	
	// public static String defaultTrackingChannelName
	
	// public static String formBotEndpointName
	
	public static String rolesCheckEnabled
	
	public static Boolean enableTestingTab

	public static String saasEventbusURL
	public static String saasServerURL
	public static String saasUsername
	public static String saasPassword
	
	public static Boolean disableSaasClient = false
			
	public static String frontWebappURL
	
	public static String webappURL
	
	static Vertx vertxInstance
	
	public static Class<? extends Account> accountType = HaleyAccount.class
	
	public static Boolean invitationCodeRequestsEnabled
	
	public static Boolean devMode 
	
	// public static Boolean uiDevMode
	
	public static Map labels = null
	
	public static Map ui = null
	
	public static Boolean autologinEnabled = false
	public static String autologinUsername = '' 
	public static String autologinPassword = ''
		
	@Override
	public void start(Future<Void> startedResult) {
		
		if(initialized) {
			startedResult.complete(true)
			return
		}
		
		vertxInstance = vertx
		
		synchronized (AgentShopWebappVerticle.class) {
			
			if(initialized) return
			
			initialized = true
		}
		
		if(context == null) context = vertx.getOrCreateContext()
		Map config = context.config()
		
		
		devMode = config.get('devMode')
		if(devMode == null) throw new RuntimeException("No devMode boolean param")
		log.info("devMode: ${devMode}")
		
		// uiDevMode = config.get('uiDevMode')
		// if(uiDevMode == null) throw new RuntimeException("No uiDevMode boolean param")
		// log.info("uiDevMode: ${uiDevMode}")
		
		appID = config.get('appID')
		if(!appID) throw new RuntimeException("No appID string param")
		log.info("appID: ${appID}")
		
		app = VitalApp.withId(appID)
		
		
		appVersion = config.get('appVersion')
		if(!appVersion) throw new RuntimeException("No appVersion string param")
		log.info("appVersion: ${appVersion}")
		
	
		appUnavailableURL = config.get('appUnavailableURL')
		if(!appUnavailableURL) throw new RuntimeException("No appUnavailableURL string param")
		log.info("appUnavailableURL: ${appUnavailableURL}")
		
		
		agentshopRestAPI = config.get('agentshopRestAPI')
		if(!agentshopRestAPI) throw new RuntimeException("No agentshopRestAPI string param")
		log.info("agentshopRestAPI: ${agentshopRestAPI}")
		
		
		pathPrefix = config.get('pathPrefix')
		if(!pathPrefix) throw new RuntimeException("No 'pathPrefix' param")
		log.info("pathPrefix: ${pathPrefix}")
		
		sessionDomain = config.get('sessionDomain')
		if(!sessionDomain) throw new RuntimeException("No 'sessionDomain' param")
		log.info("sessionDomain ${sessionDomain}")
		
		cookiePrefix = config.get('cookiePrefix')
		if(!cookiePrefix) throw new RuntimeException("No 'cookiePrefix' param")
		log.info("cookiePrefix: ${cookiePrefix}")
		
		cookieSecure = config.get('cookieSecure')
		if(cookieSecure == null) throw new RuntimeException("No 'cookieSecure' boolean param")
		log.info("cookieSecure: ${cookieSecure}")
		
		//temporarily checked on the client side
		rolesCheckEnabled = config.get('rolesCheckEnabled')
		if(rolesCheckEnabled == null) throw new RuntimeException("No 'rolesCheckEnabled' boolean param")
		log.info("rolesCheckEnabled: ${rolesCheckEnabled}")
		
		
		enableTestingTab = config.get('enableTestingTab')
		if(enableTestingTab == null) throw new RuntimeException("No 'enableTestingTab' boolean param")
		log.info("enableTestingTab: ${enableTestingTab}")
	
		
		saasServerURL = config.get('saasServerURL')
		if(!saasServerURL) throw new RuntimeException("No 'saasServerURL' param")
		log.info("saasServerURL: ${saasServerURL}")
		
		saasEventbusURL = config.get('saasEventbusURL')
		if(!saasEventbusURL) throw new RuntimeException("No 'saasEventbusURL' param")
		log.info("saasEventbusURL: ${saasEventbusURL}")
		
		disableSaasClient = config.get('disableSaasClient') != null && config.get('disableSaasClient') == true
		log.info("disableSaasClient: ${disableSaasClient}") 
		
		saasUsername = config.get('saasUsername')
		if(!saasUsername) throw new RuntimeException("No saasUsername")
		log.info("saasUsername: ${saasUsername}")
		
		saasPassword = config.get('saasPassword')
		if(!saasPassword) throw new RuntimeException("No saasPassword")
		log.info("saasPassword ${VertxUtils.maskPassword(saasPassword)}")
		
				
		// defaultTrackingChannelName = config.get('defaultTrackingChannelName')
		// if(!defaultTrackingChannelName) throw new RuntimeException("No 'defaultTrackingChannelName' param")
		// log.info("defaultTrackingChannelName: ${defaultTrackingChannelName}")
		
		// formBotEndpointName = config.get('formBotEndpointName')
		// if(!formBotEndpointName) throw new RuntimeException("No 'formBotEndpointName' param")
		// log.info("formBotEndpointName: ${formBotEndpointName}")
		
		frontWebappURL = config.get('frontWebappURL')
		if(!frontWebappURL) throw new Exception("No 'frontWebappURL' string param")
		log.info("frontWebappURL: ${frontWebappURL}")
		
		webappURL = config.getAt('webappURL')
		if(!webappURL) throw new Exception("No 'webappURL' string param")
		log.info("webappURL: ${webappURL}")
		
		String accountTypeParam = config.getAt('accountType')
		if(accountTypeParam) {
			Class accountClass = Class.forName(accountTypeParam)
			
			// if(!HaleyAccount.class.isAssignableFrom(accountClass)) {
			//   throw new Exception("accountType must be a subclass of " + HaleyAccount.class.getCanonicalName() + ' - ' + accountClass.getCanonicalName())
			// }
			
			accountType = accountClass
		}
		
		log.info("AccountType filter: " + accountType)
		
		invitationCodeRequestsEnabled = config.get('invitationCodeRequestsEnabled')
		if(invitationCodeRequestsEnabled == null) throw new Exception("No 'invitationCodeRequestsEnabled' boolean param")
		log.info("invitationCodeRequestsEnabled: ${invitationCodeRequestsEnabled}")
		
		
		labels = config.get('labels')
		if(labels == null) { throw new Exception("No labels config object")}
		log.info("labels: ${labels}")

		ui = config.get('ui')
		if(ui == null) { throw new Exception("No ui config obj")}
		log.info("ui: ${ui}")
		
		Map autologinCfg = config.get('autologin')
		if(autologinCfg != null) {
			
			autologinEnabled = autologinCfg.get('enabled') != null ? autologinCfg.get('enabled') : false
			autologinUsername = autologinCfg.get('username')
			autologinPassword = autologinCfg.get('password')
		}
		
		log.info("autloginEnabled: ${autologinEnabled}")
		log.info("autloginUsername: ${autologinUsername}")
		log.info("autloginPassword: ${maskPassword(autologinPassword)}")
		
		startedResult.complete()
				
	}

	
	static String maskPassword(String n) {
		
		if(n == null) return 'null'
		
		String output = "";

		for( int i = 0 ; i < n.length(); i++) {
				
			if(n.length() < 12 || ( i >= 4 && i < (n.length() - 4) ) ) {
				output += '*'
			} else {
				output += n.substring(i, i+1)
			}
				
		}
		
		return output
		
	}
	
	/**
	 * async operation
	 * callback called with String error, List<DomainModel>
	 */
	public static listServerDomainModels(Closure callback) {

		//the endpoint must also provide simple rest methods to validate domains
		URL websocketURL = new URL(saasServerURL)
		
		int port = websocketURL.getPort()
		boolean secure = websocketURL.getProtocol() == 'https'
		if(secure && port < 0) {
			port = 443
		} else if(port < 0){
			port = 80
		}
		
		def options = [
			// protocolVersion:"HTTP_2",
			ssl: secure,
			// useAlpn:true,
			trustAll:true
		]
		
		HttpClient client = null
		
		def onFinish = { String error, List<DomainModel> results ->
			
			try {
				if(client != null) client.close()
			} catch(Exception e) {
				log.error(e.localizedMessage, e)
			}
			
			callback(error, results)
			
		}
		
		try {
			
			client = vertxInstance.createHttpClient(options)
			
			HttpClientRequest request = client.get(port, websocketURL.host, "/domains") { HttpClientResponse response ->

				if( response.statusCode() != 200 ) {
					onFinish("HTTP Status: " + response.statusCode() + " - " + response.statusMessage(), null)
					return
				}

				response.bodyHandler { Buffer body ->

					try {
						List<GraphObject> models = JSONSerializer.fromJSONArrayString(body.toString())
						
						List<DomainModel> filtered = []
						for(GraphObject o : models) {
							if(!(o instanceof DomainModel)) throw new Exception("Expected DomainModels only: " + models.size())
							filtered.add(o)
						}
						onFinish(null, filtered)
						
					} catch(Exception e) {
						log.error(e.localizedMessage, e)
						onFinish(e.localizedMessage, null)
					}

				}
				
				response.exceptionHandler { Throwable ex ->
					log.error(ex.localizedMessage, ex)
					onFinish(ex.localizedMessage, null)
				}

			}
			
			request.exceptionHandler { Throwable ex ->
				
				log.error(ex.localizedMessage, ex)
				
				onFinish(ex.localizedMessage, null)
				
			}
			
			request.end()
			
		} catch(Exception e) {
			log.error(e.localizedMessage, e)
			onFinish(e.localizedMessage, e)
		}
		
	}
}
