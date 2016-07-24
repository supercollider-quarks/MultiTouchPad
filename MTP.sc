MTP {
	classvar <responder, <fingersDict, <activeBlobs, <>setAction, <>touchAction, <>untouchAction;
	classvar <guiOn, <guiWin, <uview;
	classvar <isRunning = false, <pid, <stopFunc, <device;
	classvar <>progpath = "";


	*initClass {
		responder = OSCresponderNode(nil, "/tuio/2Dobj", {|...args| this.processOSC(*args); });
		fingersDict = ();
		activeBlobs = List.new;
		guiOn = false;
		isRunning = false;
		stopFunc = { this.stop; };
		device = 0;
	}

	*setDevice { |argDevice|

		argDevice.switch
		(
			\internal, { device = 0; },
			\external, { device = 1; },
			{ "argDevice must be \\internal for internal trackpad and \\external for external trackpad.".error; }
		);
	}

	*start { |force = true|
		var condi = Condition();

		if (isRunning and: force.not) {
			"MultiTouchPad is already active and running. Try: \n"
			" MultiTouchPad.start(force: true);\n".error;
			^this
		};

		responder.remove;

		fork {
			condi.unhang;
			if (force) {
				"killall tongsengmod".unixCmd({ |res|
					if(res == 0, {
						"A dangling tongsengmod process was found and terminated.".postln;
						isRunning = false;
					});
					condi.unhang;
				});
				condi.hang;
				"did killall...".postln;
			};

			pid = (progpath +/+ "tongsengmod localhost"
				+ NetAddr.langPort + device.asString).postcs.unixCmd({ |res|
				"trying to start tong - res: %\n".postf(res);
				if(res == 127, {
					"tongsengmod executable not found. See help.".error;
				});
				if(res == 0, {
					"tongsengmod started.".postln;
					responder.add;
					isRunning = true;
				});
				condi.unhang;
			});
			condi.hang;

			"pid is now: %\n".postf(pid);

			ShutDown.add(stopFunc);
		};
	}

	*stop {
		if(isRunning, {
			responder.remove;
			("kill -1" + pid.asString).unixCmd;
			isRunning = false;
		}, {
			responder.remove; //in case
			"killall tongsengmod".unixCmd;
			"MultiTouchPad isn't running.".error;
		});
	}

	*processOSC { |time, responder, msg|

		//msg.postln;
		var toRemove = List.new;
		var curID = msg[2];
		var xys = msg[4..6];

		if(msg[1] == 'alive', {

			activeBlobs = msg[2..];
			fingersDict.keys.do
			({|item|

				if(activeBlobs.includes(item).not,
					{
						toRemove.add(item);
				});
			});

			toRemove.do({|item|
				fingersDict.removeAt(item);
				untouchAction.value(item);
				if(guiOn, { { guiWin.refresh; }.defer; });
			});

			activeBlobs.do({|item|

				if(fingersDict.at(item).isNil, {
					fingersDict.put(item, -1); //-1 means xy not initialized
				});
			});

			^this;
		});

		if(msg[1] == 'set', {
			if(fingersDict.at(curID).isNil, {
				"MultiTouchPad: bug? this should never happen.".postln;
			});
			if(fingersDict.at(curID) == -1, { touchAction.value(curID, xys); });
			fingersDict.put(curID, xys);
			setAction.value(curID, xys);
			if(guiOn, { { guiWin.refresh; }.defer; });
			^this;
		});
	}

	*gui { |force = false|
		if (force) { if (guiWin.notNil) { guiWin.close } };

		if (guiWin.notNil and: { MTP.guiWin.isClosed.not }) {
			guiWin.front;
			^this
		};

		guiWin = Window("MultiTouchPad", Rect(100, 100, 525, 375))
		.alpha_(0.7)
		.onClose_({ guiOn = false; });

		guiWin.view.keyDownAction = { |view, key|
			if (key == $.) { MTP.stop };
			if (key == $ ) { MTP.start };
			if (key == $m) { MTP.guiWin.fullScreen };
			if (key == $x) { MTP.guiWin.endFullScreen; };
		};

		uview = UserView(guiWin, guiWin.view.bounds)
		.background_(Color.grey(0.7)).resize_(5);
		uview.drawFunc_({

			fingersDict.keysValuesDo ({|key, fItem|

				Pen.color = Color.red;
				Pen.fillOval
				(
					Rect
					(
						guiWin.view.bounds.width * fItem[0],
						guiWin.view.bounds.height * fItem[1],
						20 * fItem[2],
						20 * fItem[2]
					)
				);
			});
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