package nz.ac.vuw.ecs.moveme;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class PSMoveClient implements Runnable {

	private static final int PSMoveClientRequestInit = 0x0;
	private static final int PSMoveClientRequestPause = 0x1;
	private static final int PSMoveClientRequestResume = 0x2;
	private static final int PSMoveClientRequestDelayChange = 0x3;
	private static final int PSMoveClientRequestConfigCamera = 0x4;
	private static final int PSMoveClientRequestCalibrateController = 0x5;
	private static final int PSMoveClientRequestLaserSetLeft = 0x7;
	private static final int PSMoveClientRequestLaserSetRight = 0x8;
	private static final int PSMoveClientRequestLaserSetBottom = 0x9;
	private static final int PSMoveClientRequestLaserSetTop = 0x10;
	private static final int PSMoveClientRequestLaserEnable = 0x11;
	private static final int PSMoveClientRequestLaserDisable = 0x12;
	private static final int PSMoveClientRequestControllerReset = 0x13;
	private static final int PSMoveClientRequestPositionSetLeft = 0x14;
	private static final int PSMoveClientRequestPositionSetRight = 0x15;
	private static final int PSMoveClientRequestPositionSetBottom = 0x16;
	private static final int PSMoveClientRequestPositionSetTop = 0x17;
	private static final int PSMoveClientRequestPositionEnable = 0x18;
	private static final int PSMoveClientRequestPositionDisable = 0x19;
	private static final int PSMoveClientRequestForceRGB = 0x20;
	private static final int PSMoveClientRequestSetRumble = 0x21;
	private static final int PSMoveClientRequestTrackHues = 0x22;
	private static final int PSMoveClientRequestCameraFrameDelayChange = 0x23;
	private static final int PSMoveClientRequestCameraFrameSetNumSlices = 0x24;
	private static final int PSMoveClientRequestCameraFramePause = 0x25;
	private static final int PSMoveClientRequestCameraFrameResume = 0x26;

	private static final int PSMoveServerPacketMagic = 0xff0000dd;
	private static final int PSMoveServerPacketCodeStandardState = 0x1;
	private static final int PSMoveServerPacketCodeCameraFrameSlice = 0x2;
	private static final int PSMoveServerPacketCodeCameraFrameState = 0x3;
	private static final int PSMoveServerMaxCons = 4;
	private static final int PSMoveServerMaxNavs = 7;
	private static final int PSMoveServerImageBufferSize = 61440;
	private static final int PSMoveServerCameraFrameSplitFormatJpg = 0x1;
	private static final int PSMoveServerMaximumCameraFrameSlices = 7;
	private static final int PSMoveServerCellPadMaxCodes = 64;

	private static final int CodeTracking = 0;
	private static final int CodeNotConnected = 1;
	private static final int CodeNotCalibrated = 2;
	private static final int CodeCalibrating = 3;
	private static final int CodeComputingAvailableColors = 4;
	private static final int CodeHueNotSet = 5;

	private static final int FlagCalibrationOccured = 0x1;
	private static final int FlagCalibrationSuceeded = 0x2;
	private static final int FlagFailCantFindSphere = 0x4;
	private static final int FlagFailMotionDetected = 0x8;

	private static final int FlagWarnMotionDetected = 0x20;

	public static final int PICK_FOR_ME = 4 << 24;
	public static final int DONT_TRACK = 2 << 24;

	private Socket tcpClient;
	private DatagramSocket udpClient;
	private OutputStream outStream;
	private DatagramPacket p;
	private int packetSize = 655360;
	private UpdateListener listener;
	private volatile boolean running;
	private int buttonsDown;

	public PSMoveClient() {
		running = false;
		tcpClient = null;
		udpClient = null;
		outStream = null;
		p = null;
	}

	/**
	 * Connect to a PlayStation running the Move.Me server program
	 * 
	 * @param server
	 *            Address of the PlayStation 3
	 * @param port
	 *            Port of the PlayStation 3
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public void connect(String server, int port) throws UnknownHostException, IOException {
		running = true;
		buttonsDown = 0;
		tcpClient = new Socket(server, port);
		tcpClient.setKeepAlive(true);
		outStream = tcpClient.getOutputStream();

		udpClient = new DatagramSocket();
		p = new DatagramPacket(new byte[packetSize], packetSize);
		int udpPort = udpClient.getLocalPort();
		System.out.println("Set up UDP server on Port: " + udpPort);

		Thread t = new Thread(this);
		t.start();
		sendCommand(PSMoveClientRequestInit, udpPort);
	}

	/**
	 * Register an update listener to get updates about the controller state. Only the last listener registered will actually get updates
	 * 
	 * @param l
	 *            The UpdateListener to register
	 */
	public void registerListener(UpdateListener l) {
		this.listener = l;
	}

	/**
	 * Close the connection with the PlayStation 3
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		tcpClient.close();
		udpClient.close();
		running = false;
		tcpClient = null;
		udpClient = null;
		outStream = null;
		p = null;
		buttonsDown = 0;
	}

	/**
	 * Pauses the standard state packet communications
	 * 
	 * @throws IOException
	 *             If the underlying TCP output steam throws an IO Exception
	 */
	public void pause() throws IOException {
		sendCommand(PSMoveClientRequestPause, 0);
	}

	/**
	 * Resume the standard state packet communications if they have been paused
	 * 
	 * @throws IOException
	 */
	public void resume() throws IOException {
		sendCommand(PSMoveClientRequestResume, 0);
	}

	/**
	 * Set the delay between packets in milliseconds. 2ms Seems to be a good value
	 * 
	 * @param delay_ms
	 *            Delay between packets
	 * @throws IOException
	 */
	public void delayChange(int delay_ms) throws IOException {
		sendCommand(PSMoveClientRequestDelayChange, delay_ms);
	}

	/**
	 * Comfigure the PlayStation eye camera.
	 * 
	 * @param maxExposure
	 *            The number of image rows of exposure time. The range is from 40 to 511. The longer the exposure time means decreased image noise but
	 *            increased motion blur, which has a negative effect on sphere tracking
	 * @param imageQuality
	 *            An image quality control know ranging from 0.0 to 1.0
	 * @throws IOException
	 */
	public void comfigureCamera(int maxExposure, float imageQuality) throws IOException {
		sendCommand(PSMoveClientRequestConfigCamera, maxExposure, imageQuality);
	}

	/**
	 * Calibrate a motion controller. It should be pointed at the camera and be held still.
	 * 
	 * @param controller
	 *            The index of the controller to calibrate (0-3)
	 * @throws IOException
	 */
	public void calibrateController(int controller) throws IOException {
		sendCommand(PSMoveClientRequestCalibrateController, controller);
	}

	/**
	 * Set the left side of the laser pointer box. The controller should be pointed at the left most point.
	 * 
	 * @param controller
	 *            Controller to set this for (0-3)
	 * @throws IOException
	 */
	public void setLaserLeft(int controller) throws IOException {
		sendCommand(PSMoveClientRequestLaserSetLeft, controller);
	}

	/**
	 * Set the right side of the laser pointer box. The controller should be pointed at the right most point.
	 * 
	 * @param controller
	 *            Controller to set this for (0-3)
	 * @throws IOException
	 */
	public void setLaserRight(int controller) throws IOException {
		sendCommand(PSMoveClientRequestLaserSetRight, controller);
	}

	/**
	 * Set the bottom side of the laser pointer box. The controller should be pointed at the bottom most point.
	 * 
	 * @param controller
	 *            Controller to set this for (0-3)
	 * @throws IOException
	 */
	public void setLaserBottom(int controller) throws IOException {
		sendCommand(PSMoveClientRequestLaserSetBottom, controller);
	}

	/**
	 * Set the top side of the laser pointer box. The controller should be pointed at the top most point.
	 * 
	 * @param controller
	 *            Controller to set this for (0-3)
	 * @throws IOException
	 */
	public void setLaserTop(int controller) throws IOException {
		sendCommand(PSMoveClientRequestLaserSetTop, controller);
	}

	/**
	 * Enable laser tracking for the specified controller
	 * 
	 * @param controller
	 *            Controller to enable (0-3)
	 * @throws IOException
	 */
	public void enableLaser(int controller) throws IOException {
		sendCommand(PSMoveClientRequestLaserEnable, controller);
	}

	/**
	 * Disable laser tracking for the specified controller
	 * 
	 * @param controller
	 *            Controller to disable (0-3)
	 * @throws IOException
	 */
	public void disableLaser(int controller) throws IOException {
		sendCommand(PSMoveClientRequestLaserDisable, controller);
	}

	/**
	 * Reset a specified controller
	 * 
	 * @param controller
	 *            Controller to reset (0-3)
	 * @throws IOException
	 */
	public void resetController(int controller) throws IOException {
		sendCommand(PSMoveClientRequestControllerReset, controller);
	}

	/**
	 * Set the left side of the position pointer box. The controller should be pointed at the left most point.
	 * 
	 * @param controller
	 *            Controller to set this for (0-3)
	 * @throws IOException
	 */
	public void setPositionLeft(int controller) throws IOException {
		sendCommand(PSMoveClientRequestPositionSetLeft, controller);
	}

	/**
	 * Set the right side of the position pointer box. The controller should be pointed at the right most point.
	 * 
	 * @param controller
	 *            Controller to set this for (0-3)
	 * @throws IOException
	 */
	public void setPositionRight(int controller) throws IOException {
		sendCommand(PSMoveClientRequestPositionSetRight, controller);
	}

	/**
	 * Set the bottom side of the position pointer box. The controller should be pointed at the bottom most point.
	 * 
	 * @param controller
	 *            Controller to set this for (0-3)
	 * @throws IOException
	 */
	public void setPositionBottom(int controller) throws IOException {
		sendCommand(PSMoveClientRequestPositionSetBottom, controller);
	}

	/**
	 * Set the top side of the position pointer box. The controller should be pointed at the top most point.
	 * 
	 * @param controller
	 *            Controller to set this for (0-3)
	 * @throws IOException
	 */
	public void setPositionTop(int controller) throws IOException {
		sendCommand(PSMoveClientRequestPositionSetTop, controller);
	}

	/**
	 * Enable position tracking for the specified controller
	 * 
	 * @param controller
	 *            Controller to enable (0-3)
	 * @throws IOException
	 */
	public void enablePosition(int controller) throws IOException {
		sendCommand(PSMoveClientRequestPositionEnable, controller);
	}

	/**
	 * Disable position tracking for the specified controller
	 * 
	 * @param controller
	 *            Controller to disable (0-3)
	 * @throws IOException
	 */
	public void disablePosition(int controller) throws IOException {
		sendCommand(PSMoveClientRequestPositionDisable, controller);
	}

	/**
	 * Force the controller to go a specific color. This disables sphere tracking, and has a significant impact on tracking accuracy
	 * 
	 * The color is in RGB. Each value ranges from 0-1
	 * 
	 * @param gem_num
	 *            Controller to set the color for (0-3)
	 * @param r
	 *            Red component (0.0 - 1.0)
	 * @param g
	 *            Green component (0.0 - 1.0)
	 * @param b
	 *            Blue component (0.0 - 1.0)
	 * @throws IOException
	 */
	public void forceRGB(int gem_num, float r, float g, float b) throws IOException {
		sendCommand(PSMoveClientRequestForceRGB, gem_num, r, g, b);
	}

	/**
	 * Set the rumble (vibration) for a specific controller. Rumble is an analog value that ranges from 0 (off) to 255 (full).
	 * 
	 * @param controller
	 *            Controller to adjust (0 - 3)
	 * @param rumble
	 *            Rumble value (0 - 255)
	 * @throws IOException
	 */
	public void setRumble(int controller, int rumble) throws IOException {
		sendCommand(PSMoveClientRequestSetRumble, controller, rumble);
	}

	/**
	 * Set the color of the controller while still keeping tracking enabled. The hues are integers from 0-259. The hues are only requests. The hues
	 * may be moved in order to facilitate tracking. The hues must be set for all controllers at once. To allow the system to choose a specific color
	 * the constant PICK_FOR_ME can be used. To disable tracking of a controller the constant DONT_TRACK can be used.
	 * 
	 * @param hue0
	 *            Hue for controller 0 (0 - 359)
	 * @param hue1
	 *            Hue for controller 1 (0 - 359)
	 * @param hue2
	 *            Hue for controller 2 (0 - 359)
	 * @param hue3
	 *            Hue for controller 3 (0 - 359)
	 * @throws IOException
	 */
	public void setTrackingColor(int hue0, int hue1, int hue2, int hue3) throws IOException {
		sendCommand(PSMoveClientRequestTrackHues, hue0, hue1, hue2, hue3);
	}

	/**
	 * Sets the delay between camera frame packets
	 * 
	 * @param image_delay_ms
	 *            delay in milliseconds random from 16 to 255 ms.
	 * @throws IOException
	 */
	public void cameraFrameDelayChange(int image_delay_ms) throws IOException {
		sendCommand(PSMoveClientRequestCameraFrameDelayChange, image_delay_ms);
	}

	/**
	 * Configure the number of horizontal slices in which each camera frame is sent
	 * 
	 * @param num_slices
	 *            Number of slices each packet is sent in. Typically, no more than 2 slices are needed. The value ranges from 1 - 7
	 * @throws IOException
	 */
	public void cameraFrameSetNumSlices(int num_slices) throws IOException {
		sendCommand(PSMoveClientRequestCameraFrameSetNumSlices, num_slices);
	}

	/**
	 * Pause camera frame packet communications
	 * 
	 * @throws IOException
	 */
	public void cameraFramePause() throws IOException {
		sendCommand(PSMoveClientRequestCameraFramePause, 0);
	}

	/**
	 * Resume camera frame packet communications
	 * 
	 * @throws IOException
	 */
	public void cameraFrameResume() throws IOException {
		sendCommand(PSMoveClientRequestCameraFrameResume, 0);
	}

	public synchronized void sendCommand(int command, int payload) throws IOException {
		ByteBuffer buff = ByteBuffer.allocate(12);
		buff.putInt(command);
		buff.putInt(4);
		buff.putInt(payload);
		outStream.write(buff.array());
		outStream.flush();
	}

	/**
	 * Send a specific command to the move me server over the TCP channel if it is still up.
	 * 
	 * @param command
	 * @param payload1
	 * @param payload2
	 * @throws IOException
	 */
	public synchronized void sendCommand(int command, int payload1, int payload2) throws IOException {
		ByteBuffer buff = ByteBuffer.allocate(16);
		buff.putInt(command);
		buff.putInt(8);
		buff.putInt(payload1);
		buff.putInt(payload2);
		outStream.write(buff.array());
		outStream.flush();
	}

	/**
	 * Send a specific command to the move me server over the TCP channel if it is still up.
	 * 
	 * @param command
	 * @param payload1
	 * @param payload2
	 * @throws IOException
	 */
	public synchronized void sendCommand(int command, int payload1, float payload2, float payload3, float payload4) throws IOException {
		ByteBuffer buff = ByteBuffer.allocate(24);
		buff.putInt(command);
		buff.putInt(16);
		buff.putInt(payload1);
		buff.putFloat(payload2);
		buff.putFloat(payload3);
		buff.putFloat(payload4);
		outStream.write(buff.array());
		outStream.flush();
	}

	/**
	 * Send a specific command to the move me server over the TCP channel if it is still up.
	 * 
	 * @param command
	 * @param payload1
	 * @param payload2
	 * @throws IOException
	 */
	public synchronized void sendCommand(int command, int payload1, int payload2, int payload3, int payload4) throws IOException {
		ByteBuffer buff = ByteBuffer.allocate(24);
		buff.putInt(command);
		buff.putInt(16);
		buff.putInt(payload1);
		buff.putInt(payload2);
		buff.putInt(payload3);
		buff.putInt(payload4);
		outStream.write(buff.array());
		outStream.flush();
	}

	/**
	 * Send a specific command to the move me server over the TCP channel if it is still up.
	 * 
	 * @param command
	 * @param payload1
	 * @param payload2
	 * @throws IOException
	 */
	public synchronized void sendCommand(int command, int payload1, float payload2) throws IOException {
		ByteBuffer buff = ByteBuffer.allocate(16);
		buff.putInt(command);
		buff.putInt(8);
		buff.putInt(payload1);
		buff.putFloat(payload2);
		outStream.write(buff.array());
		outStream.flush();
	}

	public void run() {
		int lastPacketIndex = Integer.MIN_VALUE;
		while (running) {
			try {
				udpClient.receive(p);
				ByteBuffer buf = ByteBuffer.wrap(p.getData(), 0, p.getLength());
				int magic = buf.getInt();
				int serverVersion = buf.getInt();
				int payloadCode = buf.getInt();
				int packetIndex = buf.getInt();

				if (packetIndex < lastPacketIndex || serverVersion != 1 || magic != PSMoveServerPacketMagic) {
					// System.out.println("Skipping");
					continue;
				}
				lastPacketIndex = packetIndex;
				if (payloadCode != 1) {
					System.err.println("Unimplemented payload code " + payloadCode);
				} else {
					readData(buf);
				}
			} catch (SocketException e) {
				// Means that the udpClient was closed, so the application should
				// shut down
				return;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void realFullData(ByteBuffer b) {
		// Read the server config
		int num_image_slices = b.getInt();
		int image_slice_format = b.getInt();

		// Read the client config
		int ms_delay_between_standard_packets = b.getInt();
		int ms_delay_between_camera_frame_packets = b.getInt();
		int cameraFramePacketPaused = b.getInt();

		// PS Move Status for each controller
		for (int i = 0; i < PSMoveClient.PSMoveServerMaxCons; i++) {
			int connected = b.getInt();
			int code = b.getInt();
			long flags = b.getLong();
		}

		// PS move state for each controller
		for (int i = 0; i < PSMoveClient.PSMoveServerMaxCons; i++) {
			float[] pos = new float[4];
			float[] vel = new float[4];
			float[] accel = new float[4];
			float[] quat = new float[4];
			float[] angvel = new float[4];
			float[] angaccel = new float[4];
			float[] handlePos = new float[4];
			float[] handleVel = new float[4];
			float[] handleAccel = new float[4];

			for (int j = 0; j < 4; j++) {
				pos[j] = b.getFloat();
			}
			for (int j = 0; j < 4; j++) {
				vel[j] = b.getFloat();
			}
			for (int j = 0; j < 4; j++) {
				accel[j] = b.getFloat();
			}
			for (int j = 0; j < 4; j++) {
				quat[j] = b.getFloat();
			}
			for (int j = 0; j < 4; j++) {
				angvel[j] = b.getFloat();
			}
			for (int j = 0; j < 4; j++) {
				angaccel[j] = b.getFloat();
			}
			for (int j = 0; j < 4; j++) {
				handlePos[j] = b.getFloat();
			}
			for (int j = 0; j < 4; j++) {
				handleVel[j] = b.getFloat();
			}
			for (int j = 0; j < 4; j++) {
				handleAccel[j] = b.getFloat();
			}

			// PS Move Pad data
			short digitalButtons = b.getShort();
			short trigger = b.getShort();

			long timestamp = b.getLong();
			float temperature = b.getFloat();
			float cameraPitchAngle = b.getFloat();
			int trackingFlags = b.getInt();
		}

		// PSMove image state for each controller
		for (int i = 0; i < PSMoveClient.PSMoveServerMaxCons; i++) {
			long frameTimestamp = b.getLong();
			long timestamp = b.getLong();
			float u = b.getFloat();
			float v = b.getFloat();
			float r = b.getFloat();
			float projectionX = b.getFloat();
			float projectionY = b.getFloat();
			float distance = b.getFloat();
			int visible = b.getInt();
			int rValid = b.getInt();
		}

		// PSMove pointer state for each controller (laser info)
		for (int i = 0; i < PSMoveClient.PSMoveServerMaxCons; i++) {
			int valid = b.getInt();
			float normalisedX = b.getFloat();
			float normalisedY = b.getFloat();
		}

		// PS Nav pad info
		int[] portStatus = new int[PSMoveServerMaxNavs];
		for (int i = 0; i < PSMoveServerMaxNavs; i++) {
			portStatus[i] = b.getInt();
		}

		// PS Nav pad data for each nav pad
		for (int i = 0; i < PSMoveServerMaxNavs; i++) {
			int length = b.getInt();
			short[] button = new short[PSMoveServerCellPadMaxCodes];
			for (int j = 0; j < PSMoveServerCellPadMaxCodes; j++) {
				button[j] = b.getShort();
			}
		}

		// PS Move sphere data for each controller
		for (int i = 0; i < PSMoveClient.PSMoveServerMaxCons; i++) {
			int tracking = b.getInt();
			int tackingHue = b.getInt();
			float r = b.getFloat();
			float g = b.getFloat();
			float blue = b.getFloat();
		}

		// Camera State
		int exposure = b.getInt();
		float exposureTime = b.getFloat();
		float gain = b.getFloat();
		float pitchAngle = b.getFloat();
		float pitchAngleEstimate = b.getFloat();

		// PS Move Position Pointer state;
		for (int i = 0; i < PSMoveClient.PSMoveServerMaxCons; i++) {
			int valid = b.getInt();
			float normalisedX = b.getFloat();
			float normalisedY = b.getFloat();
		}
	}

	// long lastFlag = -1;
	// int lastCode = -1;
	private void readData(ByteBuffer b) {
		boolean controller0Connected = b.getInt(40) != 0 ? true : false;
		int controller0Code = b.getInt(40 + 4);
		long controller0Flags = b.getLong(40 + 8);

		int digitalButtons = b.getShort(104 + 144);
		int analog_T = b.getShort(104 + 144 + 2);

		// boolean sphereVisible = b.get(808 + 37) != 0 ? true : false;
		boolean sphereVisible = b.getInt(808 + 40) != 0 ? true : false;

		boolean pointerStateValid = b.getInt(1000) != 0 ? true : false;
		float normalized_x = b.getFloat(1004);
		float normalized_y = b.getFloat(1008);

		boolean trackingEnabled = b.getInt(2000) != 0 ? true : false;

		boolean posPointerStateValid = b.getInt(2100) != 0 ? true : false;
		float posNormalized_x = b.getFloat(2104);
		float posNormalized_y = b.getFloat(2108);

		// if (controller0Code != lastCode) {
		// System.out.println("Code: " + controller0Code);
		// lastCode = controller0Code;
		// }
		// if (controller0Flags != lastFlag) {
		// lastFlag = controller0Flags;
		// System.out.printf("Flags: %08x\n", controller0Flags);
		// }
		if (!controller0Connected && controller0Code == 1) {
			if (listener != null) {
				listener.noController();
			}
		}

		int diff = digitalButtons ^ buttonsDown;
		int digitalButtonsPushed = diff & digitalButtons;
		int buttonsHeld = digitalButtons & buttonsDown;
		int buttonsReleased = diff & buttonsDown;

		buttonsDown = digitalButtons;

		if (!sphereVisible) {
			// System.out.println("Sphere not visible");
		}
		if (!trackingEnabled) {
			// System.out.println("Tracking not enabled");
		}

		float x, y;
		if (!pointerStateValid && !posPointerStateValid) {
			listener.positionUpdate(digitalButtonsPushed, buttonsHeld, buttonsReleased, analog_T);
			return;
		} else if (pointerStateValid) {
			x = normalized_x;
			y = normalized_y;
		} else {
			x = posNormalized_x;
			y = posNormalized_y;
		}
		if (listener != null) {
			listener.positionUpdate(x, y, digitalButtonsPushed, buttonsHeld, buttonsReleased, analog_T);
		}

	}

	public static void main(String args[]) {
		final PSMoveClient client = new PSMoveClient();
		client.registerListener(new UpdateListener() {

			@Override
			public void positionUpdate(int buttonsPushed, int buttonsHeld, int buttonsReleased, int trigger) {
				// System.out.printf("%02x %02x %02x\n", buttonsPushed, buttonsHeld, buttonsReleased);
				try {
					if ((buttonsPushed & UpdateListener.ButtonCircle) != 0) {
						client.setLaserRight(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonCross) != 0) {
						client.setLaserBottom(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonTriangle) != 0) {
						client.setLaserTop(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonSquare) != 0) {
						client.setLaserLeft(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonMove) != 0) {
						client.enableLaser(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonSelect) != 0) {
						client.resetController(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonStart) != 0) {
						client.calibrateController(0);
					}

				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void positionUpdate(float x, float y, int buttonsPushed, int buttonsHeld, int buttonsReleased, int trigger) {
				try {
					if ((buttonsPushed & UpdateListener.ButtonCircle) != 0) {
						client.setLaserRight(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonCross) != 0) {
						client.setLaserBottom(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonTriangle) != 0) {
						client.setLaserTop(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonSquare) != 0) {
						client.setLaserLeft(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonMove) != 0) {
						client.setTrackingColor(PICK_FOR_ME, PICK_FOR_ME, PICK_FOR_ME, PICK_FOR_ME);
					}
					if ((buttonsPushed & UpdateListener.ButtonSelect) != 0) {
						client.resetController(0);
					}
					if ((buttonsPushed & UpdateListener.ButtonStart) != 0) {
						client.calibrateController(0);
					}

				} catch (IOException e) {
					e.printStackTrace();
				}
//				System.out.printf("(%.3f, %.3f)\n", x, y);
			}

			@Override
			public void noController() {
				System.out.println("Controller is not connected");
			}

		});
		try {
			client.connect("130.195.11.193", 7899);
			client.delayChange(2);
			// client.disableLaser(0);
			// client.disablePosition(0);

			int[] col = { 0, 100, 250 };
			int[] step = { 1, -1, 1 };
			while (true) {
				client.setTrackingColor(col[0], col[1], col[2], PICK_FOR_ME);
				for (int i = 0; i < 3; i++) {
					col[i] = col[i] +  step[i];
					if (col[i] == 70) {
						step[i] *= -1;
						col[i] += step[i];
					}
					if (col[i] == -1) {
						col[i] = 359;
					}
					if (col[i] == 360) {
						col[i] = 0;
					}
				}
				Thread.sleep(10);
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}
}
