package processing.app;

import processing.core.PApplet;
import processing.core.PConstants;
import android.app.Fragment;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class PFragment extends Fragment implements PConstants {
	
  private PApplet applet;
  
  @Override
  public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    //TODO: remove this hardcode
    applet = new PApplet(1080, 1920);
    applet.setWrapper(this);

    return applet.getSketchView();
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
