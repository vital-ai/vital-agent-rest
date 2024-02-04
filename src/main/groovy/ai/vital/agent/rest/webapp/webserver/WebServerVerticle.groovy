package ai.vital.agent.rest.webapp.webserver

import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ai.vital.agent.rest.webapp.AgentShopWebappVerticle

class WebServerVerticle extends AbstractVerticle {

	private final static Logger log = LoggerFactory.getLogger(WebServerVerticle.class) 
	
	@Override
	public void start(Future<Void> startFuture) throws Exception {

		if(context == null) context = vertx.getOrCreateContext()
		
		Map<String, Object> webserverCfg = context.config().get("webserver")
		
		if(webserverCfg == null) {
			startFuture.fail("No webserver config part")
			return
		}
		
		// webserverCfg = config.getConfig("webserver")
		// Config webserver = config.getConfig("webserver")
		
		def router = Router.router( vertx )

		router.route().handler(CorsHandler.create("*")
			.allowedMethod(io.vertx.core.http.HttpMethod.GET)
			.allowedMethod(io.vertx.core.http.HttpMethod.POST)
			.allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
			//.allowCredentials(true)
			.allowedHeader("Access-Control-Allow-Headers")
			.allowedHeader("Authorization")
			.allowedHeader("Access-Control-Allow-Method")
			.allowedHeader("Access-Control-Allow-Origin")
			.allowedHeader("Access-Control-Allow-Credentials")
			.allowedHeader("Content-Type"));
		
		router.route().handler( { RoutingContext ctx ->
			ctx.response().putHeader("Cache-Control", "no-store, no-cache")
			ctx.next()
		})
		
		
		IndexFileHandler ifh = new IndexFileHandler()
	
		router.route("/").handler(ifh)
			
		// router.route("/index.html").handler(ifh)
		
		router.routeWithRegex(".*\\.html").handler(ifh)
		
		router.route("/home").handler(ifh)
		
		router.routeWithRegex("/home/.*").handler(ifh)
		
		router.route("/searchresults").handler(ifh)
		
		router.routeWithRegex("/searchresults/.*").handler(ifh)
		
		router.route("/registeragent").handler(ifh)
		
		router.routeWithRegex("/agentdetails/.*").handler(ifh)
		
		router.get('/status').handler(new StatusHandler())
		
		router.post('/agentsubmission').handler(new AgentSubmissionHandler( vertx ))
		
		router.post('/agentquery').handler(new AgentQueryHandler( vertx ))
		
		StaticHandler staticHandler = StaticHandler.create()
		staticHandler.setCachingEnabled(false)
		staticHandler.setFilesReadOnly(false)

		router.route().handler(staticHandler)
		
		def cfg = webserverCfg
		
		def server = vertx.createHttpServer(cfg)
		
		server.requestHandler(router.&accept)
		
		server.listen() { AsyncResult<HttpServer> res ->
		
			if(! res.succeeded() ) {
				
				log.error("Failed to start http server: ${res.cause().localizedMessage}", res.cause())
				startFuture.fail(res.cause())
				return 
			}
				
			log.info "WWW server started - host: ${cfg.host} port ${cfg.port}"
			
			startFuture.complete()
		}
	}
}
