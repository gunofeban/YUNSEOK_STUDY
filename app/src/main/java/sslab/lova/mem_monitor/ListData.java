package sslab.lova.mem_monitor;

import android.graphics.drawable.Drawable;

/**c
 * Created by Lova on 2015-09-10.
 */
public class ListData {
    public Drawable Icon;
    public String mTitle;
    public String label;
    public int mem=0;
    public boolean checkBool;
    public void setSeleceted(boolean seleceted)
    {
        this.checkBool = seleceted;
    }
    public boolean isSelected()
    {return checkBool;}
}
