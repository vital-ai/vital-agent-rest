package ai.vital.agent.rest.webapp.webserver

import io.vertx.core.Handler
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringEscapeUtils
import org.slf4j.LoggerFactory

import ai.vital.agent.rest.webapp.AgentShopWebappVerticle
import ai.vital.agent.rest.webapp.webserver.IndexFileHandler.CachedContent
import groovy.json.JsonOutput

class IndexFileHandler implements Handler<RoutingContext>{

	private final static org.slf4j.Logger log = LoggerFactory.getLogger(IndexFileHandler.class)
	
	long lastModTime = 0L
	
	static class CachedContent {
		boolean classpath
		String content
		long length
		long fileTimestamp = 0
		long lastCheckTimestamp = 0
	}
	
	static Map<String, CachedContent> cachedContent = Collections.synchronizedMap([:])
	
	public IndexFileHandler() {
	}
	
	@Override
	public void handle(RoutingContext context) {

		try {
			
			String path = context.request().path();
			
			HttpServerResponse resp = context.response()
			
			log.debug("Source Input path: ${path}")
					
			// if(path.endsWith("/")) path += "index.html"
			// TODO do this more generally
			
			if(checkExtension(path)) {
				
				// js, css, an image, keep as is	
			}
			else {
				
				// else, always rewrite to SPA
				path = "/index.html"
			}
			
			
			CachedContent cc = cachedContent.get(path)
			
			long ctime = System.currentTimeMillis()
			
			//check if file has changed if last check timestamp was 10 seconds ago
			if(cc != null && ( cc.classpath || ( !cc.classpath && ( ctime - cc.lastCheckTimestamp ) < 10000 ) ) ) {
				
				log.debug("Returned immediately cached");
				
				sendContent(cc, resp)
				
				return
				
			}
			
			
			if(cc != null) {
				
				//we have already a cached file, check timestamp
				File f = new File('./webroot' + path)
				
				if(!f.exists()) {
					cachedContent.remove(path)
					resp.setStatusCode(404).end("File not found", "UTF-8")
					return
				}
			
				long lm = f.lastModified();
				
				if(lm != cc.fileTimestamp) {
					
					log.debug("File has changed");
					
					// refresh the file
					String s = FileUtils.readFileToString(f, "UTF-8")
					
					processContent(s, cc)
					
					cc.fileTimestamp = lm

				} else {
					
					log.debug("File unchanged");
				}
				
				cc.lastCheckTimestamp = ctime
				
				sendContent(cc, resp)
				
				return
			}
			
			String classPathLocation = 'webroot' + path
			
			try {
				URL url = IndexFileHandler.class.getResource(classPathLocation)
				if(url != null) {
					String content = IOUtils.toString(url, "UTF-8")
					cc = new CachedContent()
					cc.classpath = true
					processContent(content, cc)
					cachedContent.put(path, cc)
					sendContent(cc, resp)
					return
				}
				
			} catch(Exception e) {
			}
		
			// we have already a cached file, check timestamp
			File f = new File('./webroot' + path)
				
			if(!f.exists()) {
				resp.setStatusCode(404).end("File not found", "UTF-8")
				return
			}
			
			cc = new CachedContent()
			processContent(FileUtils.readFileToString(f, "UTF-8"), cc)
			cc.classpath = false
			cc.fileTimestamp = f.lastModified()
			cc.lastCheckTimestamp = ctime
			
			cachedContent.put(path, cc)
			
			log.debug("Cached new file");
			
			sendContent(cc, resp)
		
		} catch(Exception e) {
			log.error(e.localizedMessage, e)
		}
		
		//first obtain from file path
		
		
	}

	void processContent(String s, CachedContent cc) {
		
		//just replace all ${PREFIX} variables
		String jsDomainsInclude = ''
		File jsDomainsDir = new File('./webroot/js/vitalservice/domains')
		for(File js : jsDomainsDir.listFiles()) {
			jsDomainsInclude += "\t<script src=\"${AgentShopWebappVerticle.pathPrefix}js/vitalservice/domains/${js.name}\"></script>\n"
		}
		
		File jsDir = new File('./webroot/js/vitalservice/')
		String jsVitalServiceInclude = "";
		for(File js : jsDir.listFiles()) {
			String n = js.name
			
			
			// if(ChatAppWebappVerticle.uiDevMode) {
			//	if(n.startsWith('vitalservice-mock-impl-') || n.startsWith('haley-js-mock-implementation-')) {
			//		jsVitalServiceInclude += "\t<script src=\"${ChatAppWebappVerticle.pathPrefix}js/vitalservice/${js.name}\"></script>\n"
			//	}
			// } else {
				
				
				if(n.startsWith('vitalservice-impl-')) {
					jsVitalServiceInclude += "\t<script src=\"${AgentShopWebappVerticle.pathPrefix}js/vitalservice/${js.name}\"></script>\n"
				}
				
				
			// }
		}
		
		// if(ChatAppWebappVerticle.uiDevMode) {
		// 	jsVitalServiceInclude += "\t<script src=\"${ChatAppWebappVerticle.pathPrefix}js/haley-mock.js\"></script>\n";
		// }
		//just replace all ${PREFIX} variables
		
		cc.content = s
			// .replace('${UI_DEV_MODE}', '' + ChatAppWebappVerticle.uiDevMode.booleanValue())
		 	.replace('${DEV_MODE}', '' + AgentShopWebappVerticle.devMode.booleanValue())
			.replace('${PREFIX}', AgentShopWebappVerticle.pathPrefix)
			.replace('${APPID}', AgentShopWebappVerticle.appID)
			
			.replace('${APPVERSION}', AgentShopWebappVerticle.appVersion)
			
			.replace('${APPUNAVAILABLEURL}', AgentShopWebappVerticle.appUnavailableURL)
			
			.replace('${AGENTSHOP_REST_API}', AgentShopWebappVerticle.agentshopRestAPI)
			
			
			
			// .replace('${DEFAULT_TRACKING_CHANNEL_NAME}', ChatAppWebappVerticle.defaultTrackingChannelName)
			.replace('${FRONT_WEBAPP_URL}', AgentShopWebappVerticle.frontWebappURL)
			.replace('${WEBAPP_URL}', AgentShopWebappVerticle.webappURL)
			.replace('${SAAS_SERVER_URL}', AgentShopWebappVerticle.saasServerURL)
			.replace('${SAAS_EVENTBUS_URL}', AgentShopWebappVerticle.saasEventbusURL)
			// .replace('${FORMBOT_ENDPOINT_NAME}', '' + ChatAppWebappVerticle.formBotEndpointName)
			.replace('${UI_LABELS}', '' + StringEscapeUtils.escapeJson( JsonOutput.toJson(AgentShopWebappVerticle.labels)) )
			
			.replace('${TITLE}', '' + AgentShopWebappVerticle.labels.title)
			
			.replace('${SESSION_DOMAIN}', '' + AgentShopWebappVerticle.sessionDomain)
			.replace('${COOKIE_PREFIX}', '' + AgentShopWebappVerticle.cookiePrefix)
			.replace('${COOKIE_SECURE}', '' + AgentShopWebappVerticle.cookieSecure)
			.replace('${UI_CONFIG}', '' + StringEscapeUtils.escapeJson( JsonOutput.toJson(AgentShopWebappVerticle.ui)) )
			.replace('${ROLES_CHECK_ENABLED}', '' + AgentShopWebappVerticle.rolesCheckEnabled)
			.replace('${JS_VITALSERVICE_INCLUDE}', jsVitalServiceInclude)
			.replace('${JS_DOMAINS_INCLUDE}', jsDomainsInclude)
			.replace('${ENABLE_TESTING_TAB}', '' + AgentShopWebappVerticle.enableTestingTab)
			
		if(AgentShopWebappVerticle.autologinEnabled?.booleanValue()) {
			cc.content = cc.content.replace('${AUTOLOGIN_ENABLED}', 'true').replace('${AUTOLOGIN_USERNAME}', AgentShopWebappVerticle.autologinUsername).replace('${AUTOLOGIN_PASSWORD}', AgentShopWebappVerticle.autologinPassword)
		}
			
		cc.length = cc.content.getBytes("UTF-8").length
	}
	
	void sendContent(CachedContent cc, HttpServerResponse resp) {
		
		resp.headers().add("Content-Type", "text/html; charset=UTF-8").add("Content-Length", "" + cc.length)
		resp.end(cc.content, "UTF-8")
	}
	
	boolean checkExtension(String filename) {
		return filename ==~ /(?i).*\.(webp|svg|ico|js|css|png|jpg|jpeg|gif|bmp|jfif|webm)$/
	}
	
	
}
