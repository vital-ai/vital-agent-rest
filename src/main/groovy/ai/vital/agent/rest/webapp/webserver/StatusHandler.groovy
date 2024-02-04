package ai.vital.agent.rest.webapp.webserver

import io.vertx.ext.web.RoutingContext
import java.text.DecimalFormat

import ai.vital.agent.rest.webapp.AgentShopWebappVerticle
import ai.vital.agent.rest.webapp.VertxUtils
import ai.vital.service.vertx3.binary.ResponseMessage
import groovy.json.JsonOutput;
import io.vertx.core.Handler


class StatusHandler implements Handler<RoutingContext> {

	
	static double mib = 1024d*1024d;
	
	static double gib = 1024d*1024d*1024d;
	
	static DecimalFormat df = new DecimalFormat("0.0");
	
	@Override
	public void handle(RoutingContext ctx) {

		boolean ok = true
		
		String message = ''
		
		def sendResults = { ->
			
			Map r = [ok: ok, message: message]
			ctx.response().end(JsonOutput.toJson(r), 'UTF-8')
		}
			
		message = ''
			
		Runtime runtime = Runtime.getRuntime()
			
		double maxMemory = (double) runtime.maxMemory()
		
		double usedMemory = (double) ( runtime.totalMemory() - runtime.freeMemory() )
		
		double memUsage = ( usedMemory / maxMemory ) * 100d
		
		if(memUsage > 90d) {
			
			ok = false
			
			message += ( "\n available memory below 10% (" + df.format(memUsage) + "%) - used " + df.format(usedMemory/mib) + "MiB, max: " + df.format(maxMemory/mib) + "MiB" );
		} else {
				message += ( "\n mem usage OK: " + df.format(memUsage) + "% - used " + df.format(usedMemory/mib) + "MiB, max: " + df.format(maxMemory/mib) + "MiB" )
		}

		
		File file = new File(".")
		
		double totalSpace = file.getTotalSpace()
		
		// double freeSpace = file.getFreeSpace()
			
		double usableSpace = file.getUsableSpace()
		
		double usedSpace = totalSpace - usableSpace;
	
		double diskUsage = (usedSpace / totalSpace) * 100d;
			
		if(diskUsage > 90d) {
			ok = false
			message += ( "\n available disk space below 10% (" + df.format(diskUsage) + "%) - used " + df.format(usedSpace/gib) + "GiB, total: " + df.format(totalSpace/gib) + "GiB")
		} else {
			message += ( "\n disk usage OK: " + df.format(diskUsage) + "% - used " + df.format(usedSpace/gib) + "GiB, total: " + df.format(totalSpace/gib) + "GiB")
		}
			
		sendResults()

	}
}
