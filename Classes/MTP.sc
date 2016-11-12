// only makes sense for a single multitouchpad, so killall is ok.

MTP {
	classvar <responder, <fingersDict, <activeBlobs, <>setAction, <>touchAction, <>untouchAction;
	classvar <guiOn, <guiWin, <uview, <>fingerCol, <>fingerSize, <fingerStrings;
	classvar <isRunning = false, <pid, <stopFunc, <device;
	classvar <>progpath = "/usr/local/bin", <infoText, <>extraText;
	classvar <smallRect;

	*initClass {
		responder = OSCresponderNode(nil, "/tuio/2Dobj", {|...args| this.processOSC(*args); });
		fingersDict = ();
		activeBlobs = List.new;
		guiOn = false;
		isRunning = false;
		stopFunc = { this.stop; };
		device = 0;
		fingerSize = 20;
		fingerCol = Color.red;
		fingerStrings = ();
		infoText = List[
			"space to start, . to stop",
			"m for big, x for small"
		];
	}

	*refresh {
		if (guiWin.notNil and: { guiWin.isClosed.not }) {
			defer { guiWin.refresh }
		}
	}

	*setDevice { |argDevice|

		argDevice.switch
		(
			\internal, { device = 0; },
			\external, { device = 1; },
			{ "argDevice must be \\internal for internal trackpad and \\external for external trackpad.".error; }
		);
	}

	*killAll { |func|
		"killall tongsengmod".unixCmd({ |res|
			if(res == 0, {
				"A dangling tongsengmod process was found and terminated.".postln;
				isRunning = false;
			});
			func.value;
		});
	}

	*start { |force = false|

		if (isRunning and: force.not) {
			"MTP is already active and running. Try: \n"
			" MTP.start(force: true);\n".error;
			^this
		};

		responder.remove;

		if (force) {
			this.killAll ({ MTP.prStart; });
		} {
			MTP.prStart;
		};
	}

	*prStart {
		var cmdStr = (progpath +/+ "tongsengmod localhost"
			+ NetAddr.langPort + device.asString);

		"%: tongsengmod starting.\n".postf(this);
		responder.add;
		isRunning = true;
		ShutDown.add(stopFunc);

		pid = cmdStr.unixCmd({ |res|
			if(res == 127, {
				"tongsengmod executable not found. See \n MTP.openHelpFile;".error;
				responder.remove;
				isRunning = false;
			});
			if (res == 0) {
				"%: tongsengmod stopped.\n".postf(this);
			};
		});
		this.refresh;
	}



	*stop {
		responder.remove;
		"killall tongsengmod".postcs.unixCmd;
		isRunning = false;
		"MTP stopped.".postln;
		this.refresh;
	}

	*processOSC { |time, responder, msg|

		//msg.postln;
		var toRemove = List.new;
		var curID = msg[2];
		var xys = msg[4..6];

		if(msg[1] == 'alive', {

			activeBlobs = msg[2..];
			fingersDict.keys.do ({|item|

				if(activeBlobs.includes(item).not,
					{
						toRemove.add(item);
				});
			});

			toRemove.do({|item|
				fingersDict.removeAt(item);
				untouchAction.value(item);
				fingerStrings.removeAt(item);
				this.refresh;
			});

			activeBlobs.do({|item|
				if(fingersDict.at(item).isNil, {
					fingersDict.put(item, -1); //-1 means xy not initialized
				});
			});

			^this
		});

		if(msg[1] == 'set') {
			if(fingersDict.at(curID).isNil) {
				"MTP fingerID not found - should never happen.".postln;
			} {
				if(fingersDict.at(curID) == -1, { touchAction.value(curID, xys); });
				fingersDict.put(curID, xys);
				setAction.value(curID, xys);
				this.refresh;
			}
		}
	}

	*maximize {
		if( guiWin.bounds != Window.screenBounds ){
			smallRect = guiWin.bounds; // Rect(100, 100, 525, 375);
		};
		guiWin.bounds_(Window.screenBounds);
	}

	*minimize {
		guiWin.bounds_(smallRect);
	}

	*gui { |force = false|
		if (force) { if (guiWin.notNil) { guiWin.close } };

		if (guiWin.notNil and: { guiWin.isClosed.not }) {
			^guiWin.front;
		};

		guiWin = Window("MTP", Rect(100, 100, 525, 375))
		.alpha_(0.7)
		.onClose_({ guiOn = false; guiWin = nil; uview = nil });

		guiWin.view.keyDownAction = { |view, key|
			if (key == $.) { MTP.stop };
			if (key == $ ) { MTP.start; };
			if (key == $m) { MTP.maximize };
			if (key == $x) { MTP.minimize };
		};

		uview = UserView(guiWin, guiWin.view.bounds).resize_(5)
		.background_(Color.grey(0.4));

		uview.drawFunc_({ |uv|
			var bounds = uv.bounds;
			var halfFing = MTP.fingerSize * 0.5;
			var status = ["OFF", "ON"][isRunning.binaryValue];

			uv.background_(Color.grey(isRunning.binaryValue * 0.24 + 0.38));

			Pen.stringAtPoint(status, 4@4, Font("Futura", 32), Color.white);
			(infoText ++ extraText).do { |line, i|
				Pen.stringAtPoint(line, 10@(i * 15 + 40),
					Font("Futura", 16), Color.white)
			};

			MTP.fingersDict.keysValuesDo { |key, fItem|
				var x = bounds.width - halfFing * fItem[0];
				var y = bounds.height - halfFing * fItem[1];
				var fingSize = MTP.fingerSize * fItem[2];

				Pen.color = MTP.fingerCol;
				Pen.strokeOval( Rect(x, y, fingSize, fingSize));

				Pen.stringCenteredIn(
					fingerStrings[key] ? key.asString,
					Rect.aboutPoint(x@y, 60, 30)
				);
			};
		});
		guiOn = true;
		^guiWin.front;
	}

	*resetActions {
		touchAction = {};
		untouchAction = {};
		setAction = {};
	}
}