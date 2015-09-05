package processing.app;

import processing.core.PApplet;
import processing.core.PConstants;
import android.app.Fragment;
import android.content.pm.ActivityInfo;

public class PFragment extends Fragment implements PConstants {
	
  private PApplet applet;


  public void setPApplet(PApplet applet) {
    this.applet = applet;
    applet.setWrapper(this);
  }


  @Override
  public void onResume() {
    super.onResume();
    applet.onResume();
  }


  @Override
  public void onPause() {
    super.onPause();
    applet.onPause();
  }

  
  @Override
  public void onDestroy() {
    applet.onDestroy();
    super.onDestroy();
  }

  
  @Override
  public void onStart() {
    applet.onStart();
    super.onStart();
  }


  @Override
  public void onStop() {
    applet.onStop();
    super.onStop();
  }


  public void setOrientation(int which) {
    if (which == PORTRAIT) {
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    } else if (which == LANDSCAPE) {
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
  }
}
