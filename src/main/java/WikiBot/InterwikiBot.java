package WikiBot;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.apache.commons.lang3.text.WordUtils;

import WikiBot.APIcommands.*;
import WikiBot.ContentRep.*;
import WikiBot.Core.BotPanel;

@SuppressWarnings("unused")
public class InterwikiBot extends BotPanel {
	
	private static final long serialVersionUID = 1L;

	/*
	 * This is where I initialize my custom Mediawiki bot.
	 */
	public InterwikiBot() {
		super("Scratch", "en");
		
		//Preferences
		panelName = "InterwikiBot";
		
		botUsername = "InterwikiBot";
		
		APIlimit = 30;
		revisionDepth = 5;
		
		APIthrottle = 0.5;//Minimum time between any API commands.
		waitTimeBetweenProposedCommands = 12;//Minimum time between edits.
		
		setLoggerLevel(Level.INFO);//How fine should the logger be? Visit NetworkingBase.java for logger level info.
	}
	
	/*
	 * This is where I put my bot code.
	 */
	@Override
	public void code() {
		getRevisions = false;//Don't get page revisions.
		getRevisionContent = false;//Same as above ^^.
		parseThurough = false;

		/*
		 * Here is what our example bot will do.
		 * It will get the page "Scratch Cat" from the "en" wiki.
		 * It will print it out because I like printing out data.
		 * It will append an interwiki to the page.
		 * The edit summary will be "This page needs an interwiki. ^.^ "
		 * The bot GUI will show an edit summary of "Interwiki"
		 */
		PageLocation loc = new PageLocation("Scratch Cat", "en");
		Page page = getWikiPage(loc);
		System.out.println(page);
		proposeEdit(new AppendText(loc, "\n[[de:Scratch Katze]]", "This page needs an interwiki. ^.^ "), "Interwiki");
	}
}