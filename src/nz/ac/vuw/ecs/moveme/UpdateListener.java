package nz.ac.vuw.ecs.moveme;
public interface UpdateListener {

	public static final int ButtonSelect = 1 << 0;
	public static final int ButtonTrigger = 1 << 1;
	public static final int ButtonMove = 1 << 2;
	public static final int ButtonStart = 1 << 3;
	public static final int ButtonTriangle = 1 << 4;
	public static final int ButtonCircle = 1 << 5;
	public static final int ButtonCross = 1 << 6;
	public static final int ButtonSquare = 1 << 7;

	/**
	 * Sends an update of the position of the controller when tracking has not yet been enabled. As such only button events are tracked. Most buttons
	 * are digital and only read on of. The trigger, however, is analog. As such its value ranges from 0 (off) to 255 (fully down). To get which
	 * individual buttons are pressed use the bitmasking constants from this class.
	 * 
	 * @param buttonsPushed
	 *            Buttons pushed down this tick
	 * @param buttonsHeld
	 *            Buttons still held from before
	 * @param buttonsReleased
	 *            Buttons released this tick
	 * @param trigger
	 *            State of the trigger
	 */
	public void positionUpdate(int buttonsPushed, int buttonsHeld, int buttonsReleased, int trigger);

	/**
	 * Sends an update of the position of the controller when tracking. Most buttons are digital and only read on of. The trigger, however, is analog.
	 * As such its value ranges from 0 (off) to 255 (fully down). To get which individual buttons are pressed use the bitmasking constants from this
	 * class.
	 * 
	 * @param x
	 *            Normalised x position. 0 is the center of the screen. Bounds are [-1,1]
	 * @param y
	 *            Normalised y position. 0 is the center of the screen. Bounds are [-1,1]
	 * @param buttonsPushed
	 *            Buttons pushed down this tick
	 * @param buttonsHeld
	 *            Buttons still held from before
	 * @param buttonsReleased
	 *            Buttons released this tick
	 * @param trigger
	 *            State of the trigger
	 */
	public void positionUpdate(float x, float y, int buttonsPushed, int buttonsHeld, int buttonsReleased, int trigger);
	
	/**
	 * If no controller is actually connected, this method will be called on updates
	 */
	public void noController();

}
