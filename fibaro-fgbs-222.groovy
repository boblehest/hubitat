metadata {
	definition (name: "Fibaro FGBS-222 Smart Implant", namespace: "boblehest", author: "Jørn Lode") {
		capability "Configuration"

		fingerprint deviceId: "4096", inClusters: "0x5E,0x25,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x98,0x9F,0x5B,0x31,0x60,0x70,0x56,0x71,0x75,0x7A,0x6C,0x22"
	}

	preferences {
		generate_preferences(configuration_model())
	}
}

def installed() {
	log.debug "installed"
	initialize()
}

def updated() {
	log.debug "updated"
	initialize()
}

private initialize() {
	if (!childDevices) {
		addChildDevices()
	}

	// TODO Testing
	// multiChannelAssociationV2.multiChannelAssociationSet(

	// toEndpoint(zwave.associationV2.associationSet(
	// 	groupingIdentifier: 2,
	// 	nodeId: zwaveHubNodeId
	// 	), 5).format()

	formatCommands(1..3.collect {
		zwave.associationV2.associationGet(
			groupingIdentifier: it)
	}, 500)
}

// ----------------------------------------------------------------------------
// ------------------------------- CHILD DEVICES ------------------------------
// ----------------------------------------------------------------------------

private childNetworkId(ep) {
	"${device.deviceNetworkId}-ep${ep}"
}

private addChildDevices() {
	try {
		addChildSwitches()
	} catch (e) {
		sendEvent(
			descriptionText: "Child device creation failed.",
			eventType: "ALERT",
			name: "childDeviceCreation",
			value: "failed",
			displayed: true,
		)
	}
}

// -------- Digital inputs (EP 1 & 2) --------

// TODO Add as configured
// private addChildDigitalInputs() {
// 	1..2.eachWithIndex { ep, index ->
// 		addChildDevice("Fibaro FGBS-222 Child Input",
// 			childNetworkId(ep), componentLabel: "Input ${index+1} - Digital")
// 	}
// }

// -------- Analog inputs (EP 3 & 4) --------

// TODO Add as configured
// private addChildAnalogInputs() {
// 	3..4.eachWithIndex { ep, index ->
// 		addChildDevice("Fibaro FGBS-222 Child Analog Input",
// 			childNetworkId(ep), componentLabel: "Input ${index+1} - Analog")
// 	}
// }

// -------- Outputs (EP 5 & 6) --------

private addChildSwitches() {
	5..6.eachWithIndex { ep, index ->
		addChildDevice("Fibaro FGBS-222 Child Switch",
			childNetworkId(ep), componentLabel: "Output ${index+1}")
	}
}

private setSwitch(value, channel) {
	// TODO Should we also send a Get request? How often? Can't we have the
	// device notify us whenever it changes?
	toEndpoint(zwave.switchBinaryV1.switchBinarySet(switchValue: value), channel).format()
}

def childOn(String dni) {
	// formatCommands([
	// 	toEndpoint(zwave.switchBinaryV1.switchBinarySet(switchValue: 0xff), 5),
	// 	toEndpoint(zwave.basicV1.basicGet(), 5),
	// 	toEndpoint(zwave.basicV1.basicGet(), 5),
	// 	toEndpoint(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1), 7), 
	// ], 2000)

	setSwitch(0xff, channelNumber(dni))
}

def childOff(String dni) {
	setSwitch(0, channelNumber(dni))
}

def childRefresh(String dni) {
	toEndpoint(zwave.switchBinaryV1.switchBinaryGet(), channelNumber(dni)).format()
}

// -------- Temperature sensors (EP 7-13) --------

// TODO Add external sensors as configured
// private addChildTemperatureSensors() {
// 	def ep = 7
// 	addChildDevice("Fibaro FGBS-222 Child Temperature Sensor",
// 		childNetworkId(ep), componentLabel: "Internal temperature sensor")
// }

// ----------------------------------------------------------------------------
// ----------------------------- MESSAGE PARSING ------------------------------
// ----------------------------------------------------------------------------

def parse(String description) {
	def result = []

	def cmd = zwave.parse(description)
	if (cmd) {
		result += zwaveEvent(cmd)
		log.debug "Parsed ${cmd} to ${result.inspect()}"
	} else {
		log.debug "Non-parsed event: ${description}"
	}

	result
}

private zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	// TODO Figure out what the parameter to `encapsulatedCommand` does.
	// Try removing the argument.
	def encapsulatedCommand = cmd.encapsulatedCommand([0x25: 1, 0x20: 1])
	if (encapsulatedCommand) {
		// TODO Is sourceEndPoint really not an integer?
		zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint.toInteger())
	} else {
		log.debug "Ignored encapsulated command: ${cmd}"
	}
}

private zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, ep) {
	def target = childDevices.find { it.deviceNetworkId == childNetworkId(ep) }
	// TODO Why not createEvent? Try it.
	childDevice?.sendEvent(name: "switch", value: cmd.value ? "on" : "off")
}

private zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	// TODO Are all settings properly set? Monitor the logs.
	def configuration = new XmlSlurper().parseText(configuration_model())

	def paramName = cmd.parameterNumber.toString()
	def parameterInfo = configuration.Value.find { it.@index == paramName }
	def byteSize = sizeOfParameter(parameterInfo)
	if (byteSize != cmd.size) {
		log.debug "Parameter ${paramName} has unexpected size ${cmd.size} (expected ${byteSize})"
	}

	def remoteValue = bytesToInteger(cmd.configurationValue)
	def localValue = settings[cmd.parameterNumber.toString()]

	if (localValue != remoteValue) {
		log.debug "Parameter ${paramName} has value ${remoteValue} after trying to set it to ${localValue}"
	}
}

// private zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd, ep) {
// 	log.debug "Sensor @ endpoint ${ep} has value ${cmd.scaledSensorValue}"
// 	createEvent(name: "temperature", value: cmd.scaledSensorValue)
// }

// private zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, ep) {
// 	log.debug "Switch @ endpoint ${ep} has value ${cmd.value}"
// 	createEvent(name: "switch", value: cmd.value == 0 ? "off" : "on")
// }

private zwaveEvent(hubitat.zwave.Command cmd, ep=null) {
	log.debug "Unhandled event ${cmd} (endpoint ${ep})"
	// createEvent(descriptionText: "${device.displayName}: ${cmd}")
}

// ----------------------------------------------------------------------------
// ------------------------------ CONFIGURATION -------------------------------
// ----------------------------------------------------------------------------

def configure() {
	def configuration = new XmlSlurper().parseText(configuration_model())
	def cmds = []

	configuration.Value.each {
		def settingValue = settings[it.@index.toString()].toInteger()
		def byteSize = sizeOfParameter(it)

		if (settingValue != null) {
			if (settingValue == "") {
				log.debug "Setting ${it.@index} is empty"
			}

			def index = it.@index.toInteger()
			cmds << zwave.configurationV1.configurationSet(configurationValue: integerToBytes(settingValue, byteSize), parameterNumber: index, size: byteSize)
			cmds << zwave.configurationV1.configurationGet(parameterNumber: index)
		} else {
			log.debug "Setting ${it.@index} has null value"
		}
	}
	
	log.debug "cmds: ${cmds}"
	
	formatCommands(cmds)
}

private generate_preferences(configuration_model) {
	def configuration = new XmlSlurper().parseText(configuration_model)

	configuration.Value.each {
		switch(it.@type) {
			case ["byte", "short", "four"]:
				input "${it.@index}", "number",
					title: "${it.@label}\n" + "${it.Help}",
					range: "${it.@min}..${it.@max}",
					defaultValue: "${it.@value}",
					displayDuringSetup: "${it.@displayDuringSetup}"
				break
			case "list":
				// def items = []
				// it.Item.each { items << ["${it.@value}": "${it.@label}"] }
				def items = it.Item.collect { ["${it.@value}": "${it.@label}"] }
				input "${it.@index}", "enum",
					title: "${it.@label}\n" + "${it.Help}",
					defaultValue: "${it.@value}",
					displayDuringSetup: "${it.@displayDuringSetup}",
					options: items
				break
			case "decimal":
				input "${it.@index}", "decimal",
					title: "${it.@label}\n" + "${it.Help}",
					range: "${it.@min}..${it.@max}",
					defaultValue: "${it.@value}",
					displayDuringSetup: "${it.@displayDuringSetup}"
				break
			case "boolean":
				input "${it.@index}", "bool",
					title: it.@label != "" ? "${it.@label}\n" + "${it.Help}" : "" + "${it.Help}",
					defaultValue: "${it.@value}",
					displayDuringSetup: "${it.@displayDuringSetup}"
				break
			case "paragraph":
				input title: "${it.@label}",
					description: "${it.Help}",
					type: "paragraph",
					element: "paragraph"
				break
		}
	}
}

private configuration_model() {
	'''
<configuration>
	<Value type="list" genre="config" instance="1" index="20" label="Input 1 - operating mode" value="2" size="1">
		<Help>This parameter allows to choose mode of 1st input (IN1). Change it depending on connected device.</Help>
		<Item label="Normally closed alarm input (Notification)" value="0" />
		<Item label="Normally open alarm input (Notification)" value="1" />
		<Item label="Monostable button (Central Scene)" value="2" />
		<Item label="Bistable button (Central Scene)" value="3" />
		<Item label="Analog input without internal pull-up (Sensor Multilevel)" value="4" />
		<Item label="Analog input with internal pullup (Sensor Multilevel)" value="5" />
	</Value>

	<Value type="list" genre="config" instance="1" index="21" label="Input 2 - operating mode" value="2" size="1">
		<Help>This parameter allows to choose mode of 2nd input (IN2). Change it depending on connected device.</Help>
		<Item label="Normally closed alarm input (Notification)" value="0" />
		<Item label="Normally open alarm input (Notification)" value="1" />
		<Item label="Monostable button (Central Scene)" value="2" />
		<Item label="Bistable button (Central Scene)" value="3" />
		<Item label="Analog input without internal pull-up (Sensor Multilevel)" value="4" />
		<Item label="Analog input with internal pullup (Sensor Multilevel)" value="5" />
	</Value>

	<Value type="list" genre="config" instance="1" index="24" label="Inputs orientation" value="0" size="1">
		<Help>This parameter allows reversing operation of IN1 and IN2 inputs without changing the wiring. Use in case of incorrect wiring.</Help>
		<Item label="default (IN1 - 1st input, IN2 - 2nd input)" value="0" />
		<Item label="reversed (IN1 - 2nd input, IN2 - 1st input)" value="1" />
	</Value>

	<Value type="list" genre="config" instance="1" index="25" label="Outputs orientation" value="0" size="1">
		<Help>This parameter allows reversing operation of OUT1 and OUT2 inputs without changing the wiring. Use in case of incorrect wiring.</Help>
		<Item label="default (OUT1 - 1st output, OUT2 - 2nd output)" value="0" />
		<Item label=" reversed (OUT1 - 2nd output, OUT2 - 1st output)" value="1" />
	</Value>

	<Value type="list" genre="config" instance="1" index="40" label="Input 1 - sent scenes" value="0" size="1">
		<Help>This parameter defines which actions result in sending scene ID and attribute assigned to them. Parameter is relevant only if parameter 20 is set to 2 or 3</Help>
		<Item label="No scenes sent" value="0" />
		<Item label="Key pressed 1 time" value="1" />
		<Item label="Key pressed 2 times" value="2" />
		<Item label="Key pressed 3 times" value="4" />
		<Item label="Key hold down and key released" value="8" />
	</Value>

	<Value type="list" genre="config" instance="1" index="41" label="Input 2 - sent scenes" value="0" size="1">
		<Help>This parameter defines which actions result in sending scene ID and attribute assigned to them. Parameter is relevant only if parameter 21 is set to 2 or 3.</Help>
		<Item label="No scenes sent" value="0" />
		<Item label="Key pressed 1 time" value="1" />
		<Item label="Key pressed 2 times" value="2" />
		<Item label="Key pressed 3 times" value="4" />
		<Item label="Key hold down and key released" value="8" />
	</Value>

	<Value type="short" genre="config" instance="1" index="47" label="Input 1 - value sent to 2nd association group when activated" min="0" max="255" value="255">
		<Help>
			This parameter defines value sent to devices in 2nd association group when IN1 input is triggered (using Basic Command Class).
			Available settings: 0-255.
			Default setting: 255.
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="49" label="Input 1 - value sent to 2nd association group when deactivated" min="0" max="255" value="255">
		<Help>
			This parameter defines value sent to devices in 2nd association group when IN1 input is deactivated (using Basic Command Class).
			Available settings: 0-255.
			Default setting: 255.
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="52" label="Input 2 - value sent to 3rd association group when activated" min="0" max="255" value="255">
		<Help>
			This parameter defines value sent to devices in 3rd association group when IN2 input is triggered (using Basic Command Class).
			Available settings: 0-255.
			Default setting: 255.
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="54" label="Input 2 - value sent to 3rd association group when deactivated" min="0" max="255" value="255">
		<Help>
			This parameter defines value sent to devices in 3rd association group when IN2 input is deactivated (using Basic Command Class).
			Available settings: 0-255.
			Default setting: 255.
		</Help>
	</Value>

	<Value type="byte" genre="config" instance="1" index="150" label="Input 1 - sensitivity" min="1" max="100" value="10">
		<Help>
			This parameter defines the inertia time of IN1 input in alarm modes.
			Adjust this parameter to prevent bouncing or signal disruptions. Parameter is relevant only if parameter 20 is set to 0 or 1 (alarm mode).
			Available settings: 1-100 (10ms-1000ms, 10ms step).
			Default setting: 10 (100ms).
		</Help>
	</Value>

	<Value type="byte" genre="config" instance="1" index="151" label="Input 2 - sensitivity" min="1" max="100" value="10">
		<Help>
			This parameter defines the inertia time of IN2 input in alarm modes.
			Adjust this parameter to prevent bouncing or signal disruptions. Parameter is relevant only if parameter 21 is set to 0 or 1 (alarm mode).
			Available settings: 1-100 (10ms-1000ms, 10ms step).
			Default setting: 10 (100ms).
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="152" label="Input 1 - delay of alarm cancellation" min="0" max="3600" value="0">
		<Help>
			This parameter defines additional delay of cancelling the alarm on IN1 input. Parameter is relevant only if parameter 20 is set to 0 or 1 (alarm mode).
			Available settings:
			0 - no delay.
			1-3600s.
			Default setting: 0 (no delay).
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="153" label="Input 2 - delay of alarm cancellation" min="0" max="3600" value="0">
		<Help>
			This parameter defines additional delay of cancelling the alarm on IN2 input. Parameter is relevant only if parameter 21 is set to 0 or 1 (alarm mode).
			Available settings:
			0 - no delay.
			1-3600s.
			Default setting: 0 (no delay).
		</Help>
	</Value>

	<Value type="list" genre="config" instance="1" index="154" label="Output 1 - logic of operation" value="0" size="1">
		<Help>This parameter defines logic of OUT1 output operation.</Help>
		<Item label="contacts normally open" value="0" />
		<Item label="contacts normally closed" value="1" />
	</Value>

	<Value type="list" genre="config" instance="1" index="155" label="Output 2 - logic of operation" value="0" size="1">
		<Help>This parameter defines logic of OUT2 output operation.</Help>
		<Item label="contacts normally open" value="0" />
		<Item label="contacts normally closed" value="1" />
	</Value>

	<Value type="short" genre="config" instance="1" index="156" label="Output 1 - auto off" min="0" max="27000" value="0">
		<Help>
			This parameter defines time after which OUT1 will be automatically deactivated.
			Available settings:
			0 - auto off disabled.
			1-27000 (0.1s-45min, 0.1s step).
			Default setting: 0 (auto off disabled).
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="157" label="Output 2 - auto off" min="0" max="27000" value="0">
		<Help>
			This parameter defines time after which OUT2 will be automatically deactivated.
			Available settings:
			0 - auto off disabled.
			1-27000 (0.1s-45min, 0.1s step).
			Default setting: 0 (auto off disabled).
		</Help>
	</Value>

	<Value type="byte" genre="config" instance="1" index="63" label="Analog inputs - minimal change to report" min="0" max="100" value="5">
		<Help>
			This parameter defines minimal change (from the last reported) of
			analog input value that results in sending new report. Parameter is
			relevant only for analog inputs (parameter 20 or 21 set to 4 or 5).
			Available settings:
			0 - (reporting on change disabled).
			1-100 (0.1-10V, 0.1V step).
			Default setting: 5 (0.5V).
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="64" label="Analog inputs - periodical reports" min="0" max="32400" value="0">
		<Help>
			This parameter defines reporting period of analog inputs value.
			Periodical reports are independent from changes in value (parameter 63). Parameter is relevant only for analog inputs (parameter
			20 or 21 set to 4 or 5).
			Available settings:
			0 (periodical reports disabled).
			60-32400 (60s-9h).
			Default setting: 0 (periodical reports disabled).
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="65" label="Internal temperature sensor - minimal change to report" min="0" max="255" value="5">
		<Help>
			This parameter defines minimal change (from the last reported)
			of internal temperature sensor value that results in sending new
			report.
			Available settings:
			0 - (reporting on change disabled).
			1-255 (0.1-25.5C).
			Default setting: 5 (0.5C).
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="66" label="Internal temperature sensor - periodical reports" min="0" max="32400" value="0">
		<Help>
			This parameter defines reporting period of internal temperature
			sensor value. Periodical reports are independent from changes in
			value (parameter 65).
			Available settings:
			0 (periodical reports disabled).
			60-32400 (60s-9h).
			Default setting: 0 (periodical reports disabled).
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="67" label="External sensors - minimal change to report" min="0" max="255" value="5">
		<Help>
			This parameter defines minimal change (from the last reported) of
			external sensors values (DS18B20 or DHT22) that results in sending new
			report. Parameter is relevant only for connected DS18B20 or DHT22
			sensors.
			Available settings:
			0 - (reporting on change disabled).
			1-255 (0.1-25.5 units).
			Default setting: 5 (0.5 units)
		</Help>
	</Value>

	<Value type="short" genre="config" instance="1" index="68" label="External sensors - periodical reports" min="0" max="32400" value="0">
		<Help>
			This parameter defines reporting period of analog inputs value.
			Periodical reports are independent from changes in value (parameter 67).
			Parameter is relevant only for connected DS18B20 or DHT22 sensors.
			Available settings:
			0 - (periodical reports disabled).
			60-32400 (60s-9h).
			Default setting: 0 (periodical reports disabled).
		</Help>
	</Value>
</configuration>
	'''
}

// ----------------------------------------------------------------------------
// --------------------------------- HELPERS ----------------------------------
// ----------------------------------------------------------------------------

private toEndpoint(cmd, endpoint) {
	zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: endpoint)
		.encapsulate(cmd)
}

private formatCommands(cmds, delay=null) {
	def formattedCmds = cmds.collect { it.format() }

	if (delay) {
		delayBetween(formattedCmds, delay)
	} else {
		formattedCmds
	}
}
	
private bytesToInteger(array) {
	array.inject(0) { result, i -> (result << 8) | i }
}

private integerToBytes(value, length) {
	(length-1..0).collect { (value >> (it * 8)) & 0xFF }
}

private sizeOfParameter(paramData) {
	def typeSizes = [short: 2, four: 4, list: paramData.@size]
	typeSizes[paramData.@type] ?: 1
}

private channelNumber(String dni) {
	dni.split("-ep")[-1].toInteger()
}
