package WikiBot.Core;
 
import java.io.*;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList; 
import java.util.ConcurrentModificationException;
import java.util.Locale;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;

import WikiBot.APIcommands.APIcommand;
import WikiBot.APIcommands.Query.*;
import WikiBot.ContentRep.ImageInfo;
import WikiBot.ContentRep.Page;
import WikiBot.ContentRep.PageLocation;
import WikiBot.ContentRep.Revision;
import WikiBot.ContentRep.SimplePage;
import WikiBot.Errors.NetworkError;
import WikiBot.MediawikiData.MediawikiDataManager;

/**
 * GenericBot is an API used to interface with Mediawiki.
 * 
 * Most bot methods may be found in this class.
 * Some logger methods may be found in NetworkingBase.
 * 
 * Implementation:
 * To create a bot, make a new class that extends GenericBot.
 * You will need to provide a main method to run code.
 * 
 * To create a bot with a GUI, check out BotPanel.
 * 
 * @author: ErnieParke/Choco31415
 */
@SuppressWarnings("serial")
public class GenericBot extends NetworkingBase {
	
	protected final long serialVersionUID = 1L;
	private static GenericBot instance;
	
	//Class variables
	public MediawikiDataManager mdm;//Access to the MDM class.
	protected final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
	protected String baseURL = "http://wiki.scratch.mit.edu/w";//The url on which the bot is currently operating.
	
	//These variables keep track of data concerning the status of the bot.
	protected ArrayList<String> loggedInAtLanguages = new ArrayList<String>();//This arraylist keeps track of which languages you are logged in at.
	protected long lastCommandTimestamp = 0;//The timestamp of the last API command.

	//Configuration variables.
	protected int APIlimit = 10;//The [hard] maximum items per query call. 
	protected int revisionDepth = 10;//The number of revisions to include per page.
	protected boolean getRevisions = false;//When getting a page, should additional API calls be made to fetch revision history?
	protected boolean getRevisionContent = false;//When getting a page, should revision page content be queried?
	protected boolean logPageDownloads = true;//Should the bot log page downloads?
	public boolean parseThurough = true;//Will make additional query calls to resolve page parsing disambiguates.
	protected double APIthrottle = 0.5;//The minimum amount of time between API commands.
	
	protected final String homeWikiLanguage;//The default wiki of a bot.
	protected String family = "";//The wiki family your bot works in.
	
	public GenericBot(String family_, String homeWikiLanguage_) {				
		//Read in some files.
		mdm = new MediawikiDataManager();
		
		//Read in the bot family info.
		family = family_;
		mdm.readFamily(family_, 0);
		
		if (instance == null) {
			instance = this;
		} else {
			throw new ConcurrentModificationException();//There should not be more then one GenericBot!!!
		}
		
		//Set variables
		homeWikiLanguage = homeWikiLanguage_;
	}
	
	public static GenericBot getInstance() {
		if (instance == null) {
			throw new NullPointerException("This is not how GenericBot works.");
		}
		return instance;
	}
	
	public Page getWikiPage(PageLocation loc) {
		//This method fetches a Wiki page.
		String XMLcode = getWikiPageXMLCode(loc);
		
		return parseWikiPage(XMLcode);
	}
	
	public SimplePage getWikiSimplePage(PageLocation loc) {
		//This method fetches a Wiki page.
		String XMLcode = getWikiPageXMLCode(loc);
		
		return parseWikiPageSimple(XMLcode);
	}
	
	public boolean doesPageExist(PageLocation loc) {
		String XMLcode = getWikiPageXMLCode(loc);
		
		return !XMLcode.contains("\"pages\":{\"-1\"");
	}
	
	private String getWikiPageXMLCode(PageLocation loc) {		
		String page = APIcommand(new QueryPageContent(loc));
		
	    if (logPageDownloads) {
	    	logFine(baseURL + " // " + loc.getTitle() + " is downloaded.");
	    }
	    
	    return page;
	}
	
	public ArrayList<Page> getWikiPages(ArrayList<PageLocation> locs) {
		
		ArrayList<Page> pages = new ArrayList<Page>();
		
		String XMLcode = getWikiPagesXMLCode(locs);
		
		ArrayList<String> pageXMLstrings = getXMLItems(XMLcode, "pageid", "\"}]}", 0);
		for (String st : pageXMLstrings) {
			pages.add(parseWikiPage(st + "\"}]}"));
		}
		
		return pages;
	}
	
	public ArrayList<SimplePage> getWikiSimplePages(ArrayList<PageLocation> locs) {		
		ArrayList<SimplePage> simplePages = new ArrayList<SimplePage>();
		
		String XMLstring = getWikiPagesXMLCode(locs);
		
		ArrayList<String> pageXMLstrings = getXMLItems(XMLstring, "pageid", "\"}]}", 0);
		for (String st : pageXMLstrings) {
			simplePages.add(parseWikiPageSimple(st + "\"}]}"));
		}
		
		return simplePages;
	}
	
	private String getWikiPagesXMLCode(ArrayList<PageLocation> locs) {
		if (locs.size() == 0) {
			return null;
		}
		
		String XMLstring = APIcommand(new QueryPageContent(locs));
		
		//Log stuff
        if (logPageDownloads) {
        	if (locs.size() > 1) {
        		logFine(baseURL + " // " + locs.get(0).getTitle()
        				+ " through " + locs.get(locs.size()-1).getTitle()
        				+ " is downloaded.");
        		
        		//Super detailed log output
        		String finest = "Specifically, this includes: "; 
        		for (int i = 0; i < locs.size(); i++) {
        			if (i != 0) {
        				finest += ", ";
        			}
        			finest += locs.get(i).getTitle();
        		}
        		logFinest(finest);
        		
        	} else {
        		logFine(baseURL + " // " + locs.get(0).getTitle() + " is downloaded.");
        	}
        }
        
        return XMLstring;
	}

	protected SimplePage parseWikiPageSimple(String XMLcode) {
		/*
		 * This is a custom built XML parser for Wiki pages.
		 * It creates a SimplePage object.
		 **/
		
		SimplePage newPage = null;
		String title = parseXMLforInfo("title", XMLcode, "\",", 3, 0);
		try {
			newPage = new SimplePage(title, mdm.getLanguageFromURL(baseURL), Integer.parseInt(parseXMLforInfo("pageid", XMLcode, ",", 2, 0)));
		} catch (NumberFormatException e) {
			throw new Error("Incorrect page name: " + title + " BaseURL: " + baseURL);
		}
		newPage.setRawText(XMLcode.substring(XMLcode.indexOf("\"*\"") + 5, XMLcode.indexOf("\"}]}")));
		
		return newPage;
	}
	
	protected Page parseWikiPage(String XMLcode) {
		/*
		 * This is a custom built XML parser for Wiki pages.
		 * It creates a Page object.
		 **/
		
		Page newPage = null;
		String title = parseXMLforInfo("title", XMLcode, "\",", 3, 0);
		try {
			newPage = new Page(title, Integer.parseInt(parseXMLforInfo("pageid", XMLcode, ",", 2, 0)), mdm.getLanguageFromURL(baseURL));
		} catch (NumberFormatException e) {
			throw new Error("Incorrect page name: " + title + " BaseURL: " + baseURL);
		}
		newPage.setRawText(XMLcode.substring(XMLcode.indexOf("\"*\":") + 5, XMLcode.indexOf("\"}]}")));
		
		getPastRevisions(newPage);
		return newPage;
	}
	
	/**
	 * @param The page to attach revisions to.
	 */
	private void getPastRevisions(Page page) {
		//This method fetches the revisions of a page, if needed.
		if (getRevisions) {
			String returned = APIcommand(new QueryPageRevisions(page.getPageLocation(), revisionDepth, getRevisionContent));
			
			//Parse page for info.
			if (getRevisionContent) {
				page.setRevisions(getRevisionsFromXML(returned, "<rev user=", "</rev>", true, page.getTitle()));
			} else {
				page.setRevisions(getRevisionsFromXML(returned, "<rev user=", "\" />", false, page.getTitle()));
			}
		}
	}
	
	public ArrayList<Revision> getPastRevisions(PageLocation loc, int localRevisionDepth, boolean getContent) {
		//This method fetches the revisions of a page.
		String returned = APIcommand(new QueryPageRevisions(loc, Math.min(localRevisionDepth, APIlimit), getContent));
		
		//Parse page for info.
		if (getContent) {
			return getRevisionsFromXML(returned, "<rev user=", "</rev>", true, loc.getTitle());
		} else {
			return getRevisionsFromXML(returned, "<rev user=", "\" />", false, loc.getTitle());
		}
	}
	
	/**
	 * This method gets 30 recent changes per query call, so it might make multiple query calls.
	 * @param depth The amount of revisions you want returned.
	 * @return A list of recent changes wrapped in revisions.
	 */
	public ArrayList<Revision> getRecentChanges(String language, int depth) {
		//This method fetches the recent changes.
		ArrayList<Revision> toReturn = new ArrayList<Revision>();
		String rccontinue = null;//Used to continue queries.
		
		logFine("Getting recent changes.");
		
		do {
			//Make a query call.
			String returned;
			if (rccontinue == null) {
				returned = APIcommand(new QueryRecentChanges(language, Math.min(30, APIlimit)));
			} else {
				returned = APIcommand(new QueryRecentChanges(language, Math.min(30, APIlimit), rccontinue));
			}
			ArrayList<Revision> returnedRevisions = getRevisionsFromXML(returned, "<rc type=", "\" />", false, null);
		
			//Make sure we return the correct amount of items.
			int numRevisionsNeeded = depth - toReturn.size();
			if (returnedRevisions.size() > numRevisionsNeeded) {
				toReturn.addAll(returnedRevisions.subList(0, numRevisionsNeeded));
			} else {
				toReturn.addAll(returnedRevisions);
			}
			
			//Try continuing the query.
			try {
				rccontinue = parseXMLforInfo("rccontinue", returned, "\"");
				
				logFiner("Next page batch starts at: " + rccontinue);
			} catch (IndexOutOfBoundsException e) {
				rccontinue = null;
			}
		} while (rccontinue != null && toReturn.size() != depth);
		 
		 return toReturn;
	}
	
	/**
	 * This method gets 100 category members per query call, so it might make multiple query calls.
	 * @param loc The page location of the category.
	 * @return Returns an arraylist of all pages in a category.
	 */
	public ArrayList<PageLocation> getCategoryPages(PageLocation loc) {
		String returned;
		ArrayList<PageLocation> toReturn = new ArrayList<PageLocation>();
		String cmcontinue = null;
		
		logFine("Getting category pages.");
		
		do {
			//Make a query call.
			if (cmcontinue == null) {
				returned = APIcommand(new QueryCategoryMembers(loc.getLanguage(), loc.getTitle(), Math.min(100, APIlimit)));
			} else {
				returned = APIcommand(new QueryCategoryMembers(loc.getLanguage(), loc.getTitle(), Math.min(100, APIlimit), cmcontinue));
			}

			//Parse page for info.
			ArrayList<String> pageNames = getPages(returned, "<cm pageid=", "/>");
			
			//Transfer page names into wrapper class.
			for (String pageName : pageNames) {
				PageLocation loc2 = new PageLocation(pageName, loc.getLanguage());
				toReturn.add(loc2);
			}	
			
			//Try continuing the query.
			try {
				cmcontinue = parseXMLforInfo("cmcontinue", returned, "\"");
				
				logFiner("Next page batch starts at: " + cmcontinue);
			} catch (IndexOutOfBoundsException e) {
				cmcontinue = null;
			}
		} while (cmcontinue != null);
		
		return toReturn;
	}
	
	public ArrayList<PageLocation> getCategoryPagesRecursive(PageLocation loc) {
		ArrayList<PageLocation> pageLocs = new ArrayList<PageLocation>();
		ArrayList<PageLocation> toAdd = new ArrayList<PageLocation>();
		
		toAdd = getCategoryPages(loc);
		
		logFiner("Getting category pages (recursive) for: " + loc.getTitle());
		
		/*
		 * Look through all page names. If there is a category, get those pages.
		 */
		for (PageLocation pageLoc : toAdd) {
			if (pageLoc.getNameSpace().equals("Category")) {
				pageLocs.addAll(getCategoryPagesRecursive(pageLoc));
			} else {
				pageLocs.add(pageLoc);
			}
		}
		
		return pageLocs;
	}
	
	/**
	 * @param ignore Do not include these categories and pages in the returned result.
	 * @param loc The page location.
	 */
	public ArrayList<PageLocation> getCategoryPagesRecursive(PageLocation loc, ArrayList<String> ignore) {
		ArrayList<PageLocation> pageLocs = new ArrayList<PageLocation>();
		ArrayList<PageLocation> toAdd = new ArrayList<PageLocation>();
		
		toAdd = getCategoryPages(loc);
		
		logFiner("Getting category pages (recursive) for: " + loc.getTitle());
		
		/*
		 * Look through all page names. If there is a category, get those pages.
		 */
		for (PageLocation pageLoc : toAdd) {
			if (!ignore.contains(pageLoc.getTitle())) {
				if (pageLoc.getNameSpace().equals("Category")) {
					pageLocs.addAll(getCategoryPagesRecursive(pageLoc, ignore));
				} else {
					pageLocs.add(pageLoc);
				}
			}
		}
		
		return pageLocs;
	}
	
	/*
	 * This method gets all pages that links to loc.
	 */
	public ArrayList<PageLocation> getPagesThatLinkTo(PageLocation loc) {
		return getPagesThatLinkTo(loc, Integer.MAX_VALUE);
	}
	
	/**
	 * This method gets 30 recent changes per query call, so it might make multiple query calls.
	 * @param loc The page location to get back links for.
	 * @param depth The amount of pages to get.
	 * @return A list of page that link to loc.
	 */
	public ArrayList<PageLocation> getPagesThatLinkTo(PageLocation loc, int depth) {
		//This method gets all the pages that link to another page. Redirects are included.
		ArrayList<PageLocation> toReturn = new ArrayList<PageLocation>();
		String blcontinue = null;
		
		logFine("Getting pages that link to: "  + loc.getTitle());
		
		do {
			//Make a query call.
			String returned;
			if (blcontinue == null) {
				returned = APIcommand(new QueryBackLinks(loc, Math.min(30, APIlimit)));
			} else {
				returned = APIcommand(new QueryBackLinks(loc, Math.min(30, APIlimit), blcontinue));
			}
	
			//Parse page for info.
			ArrayList<String> pageTitles = getPages(returned, "<bl pageid=", "/>");
			
			//Transfer page names into wrapper class.
			for (String title : pageTitles) {
				if (toReturn.size() != depth) {
					toReturn.add(new PageLocation(title, loc.getLanguage()));
				} else {
					break;
				}
			}
			
			//Try continuing the query.
			try {
				blcontinue = parseXMLforInfo("blcontinue", returned, "\"");
			} catch (IndexOutOfBoundsException e) {
				blcontinue = null;
			}
		} while (blcontinue != null && toReturn.size() != depth);
		
		return toReturn;
	}
	
	/**
	 * This method gets all pages starting from the beginning.
	 * @param language
	 * @param depth
	 * @return
	 */
	public ArrayList<PageLocation> getAllPages(String language, int depth) {
		return getAllPages(language, depth, null, null);
	}
	
	/**
	 * This method gets all pages starting from the parameter "from".
	 * @param language
	 * @param depth
	 * @param from
	 * @return
	 */
	public ArrayList<PageLocation> getAllPages(String language, int depth, String from) {
		return getAllPages(language, depth, from, null);
	}
	
	/**
	 * This method gets 30 pages per query call, so it might make multiple query calls.
	 * @param language
	 * @param depth
	 * @param from
	 * @param apnamespace The id of the namespace being crawled.
	 * @return
	 */
	public ArrayList<PageLocation> getAllPages(String language, int depth, String from, Integer apnamespace) {
		ArrayList<PageLocation> toReturn = new ArrayList<PageLocation>();
		String apcontinue = null;
		
		logFine("Getting all pages starting from " + from + ".");
		
		do {
			//Make query call.
			String returned;
			if (apnamespace == null) {
				if (apcontinue == null) {
					if (from != null) {
						returned = APIcommand(new QueryAllPages(language, APIlimit, from));
					} else {
						returned = APIcommand(new QueryAllPages(language, APIlimit));
					}
				} else {
					returned = APIcommand(new QueryAllPages(language, APIlimit, apcontinue));
				}
			} else {
				if (apcontinue == null) {
					if (from != null) {
						returned = APIcommand(new QueryAllPages(language, APIlimit, from, apnamespace));
					} else {
						returned = APIcommand(new QueryAllPages(language, APIlimit, apnamespace));
					}
				} else {
					returned = APIcommand(new QueryAllPages(language, APIlimit, apcontinue, apnamespace));
				}
			}
			
			//Parse text returned.
			ArrayList<String> pageTitles= getPages(returned, "<p pageid=", "/>");
			
			//Transfer page names into wrapper class.
			for (String title : pageTitles) {
				if (toReturn.size() != depth) {
					toReturn.add(new PageLocation(title, language));
				} else {
					break;
				}
			}
			
			//Try continuing the query.
			try {
				apcontinue = parseXMLforInfo("apcontinue", returned, "\"");
				apcontinue = apcontinue.replace("_", " ");
				
				logFiner("Next page batch starts at: " + apcontinue);
			} catch (IndexOutOfBoundsException e) {
				apcontinue = null;
			}
		} while (apcontinue != null && toReturn.size() != depth);
		
		return toReturn;
	}
	
	/**
	 * Warning: Only supported in MW v.1.23 and above!
	 * @param language The language of the wiki.
	 * @param prefix The prefix that you are searching for.
	 * @return A list of pages with the given prefix.
	 */
	public ArrayList<PageLocation> getPagesByPrefix(String language, String prefix) {
		ArrayList<PageLocation> toReturn = new ArrayList<PageLocation>();
		Integer psoffset = null;
		
		do {
			//Make query call.
			String returned;
			if (psoffset == null) {
				returned = APIcommand(new QueryPrefix(language, prefix));
			} else {
				returned = APIcommand(new QueryPrefix(language, prefix, psoffset));
			}
			
			//Parse text returned.
			ArrayList<String> pageTitles= getPages(returned, "<ps", "/>");
			
			//Transfer page names into wrapper class.
			for (String pageName : pageTitles) {
				PageLocation loc2 = new PageLocation(pageName, language);
				toReturn.add(loc2);
			}	
			
			//Try continuing the query.
			try {
				psoffset = Integer.parseInt(parseXMLforInfo("psoffset", returned, "\""));
			} catch (IndexOutOfBoundsException e) {
				psoffset = null;
			}
		} while (psoffset != null);
		
		logFine("All pages with prefix " + prefix + " queried.");
		
		return toReturn;
	}
	
	/**
	 * The list of accepted properties to query is here: https://www.mediawiki.org/wiki/API:Imageinfo
	 * @param loc The pageLocation of the file.
	 * @param propertyNames The list of properties you are querying for.
	 * @return A ImageInfo class containing
	 */
	protected ImageInfo getImageInfo(PageLocation loc, ArrayList<String> propertyNames) {
		logFine("Getting file info for: " + loc.getTitle());
		logFiner("Getting properties: " + compactArray(propertyNames, ", "));
		
		String xmlString = APIcommand(new QueryImageInfo(loc, propertyNames));
		
		if (xmlString.contains("\"missing\":\"\"")) {
			logError(loc.getTitle() + " does not exist.");
			return null;
		}
		
		ImageInfo toReturn = new ImageInfo(loc);
		
		int i = 0;
		do {
			String name = propertyNames.get(i);
			String value = "";
			
			//Get the parameter's value from the MediaWiki output.
			if (name.equalsIgnoreCase("dimensions")) {
				//Dimension returns two numbers.
				propertyNames.add("width");
				propertyNames.add("height");
			} else {
				value = parseXMLforInfo( name + "\"", xmlString, ",", 1, 0);
				
				//Any value surrounded with "" is a String, and the "" should be removed.
				if (value.substring(0, 1).equals("\"") && value.substring(value.length()-1, value.length()).equals("\"")) {
					value = value.substring(1, value.length()-1);
				}
				
				toReturn.addProperty(name, value);
			}
			
			i += 1;
		} while (i < propertyNames.size());
		
		return toReturn;
	}
	
	/**
	 * 
	 * @param loc The pageLocation of the file.
	 * @return The url, dimensions, and media type of the image.
	 */
	protected ImageInfo getImageInfo(PageLocation loc) {
		ArrayList<String> properties = new ArrayList<String>();
		properties.add("url");
		properties.add("dimensions");
		properties.add("size");
		return getImageInfo(loc, properties);
	}
	
	/**
	 * @param loc The pageLocation of the file.
	 * @return A String of the url that goes directly to the image file (and nothing else).
	 */
	protected String getDirectImageURL(PageLocation loc) {
		ArrayList<String> properties = new ArrayList<String>();
		properties.add("url");
		ImageInfo info = getImageInfo(loc, properties);
		return info.getProperty("url");
	}
	
	public boolean logIn(String username, String password, String language) {
        HttpEntity entity = null;
        
        baseURL = mdm.getWikiURL(language);
        
        try {
        	logCookies();
        } catch (NullPointerException e) {
        	logFinest("No cookies detected.");
        }

        //LOG IN
        String token = null;
        String xmlString = "";
        for (int j = 0; j < 2; j++) {
    		//Check throttle.
    		throttleAction();
    		
    		//Send POST request.
        	if (token == null) {
        		entity = getPOST(baseURL + "/api.php?action=login&format=xml", new String[]{"lgname", "lgpassword"}, new String[]{username, password});
        	} else {
        		entity = getPOST(baseURL + "/api.php?action=login&format=xml", new String[]{"lgname", "lgpassword", "lgtoken"}, new String[]{username, password, token});
        	}
        	
	        logCookies();
	        
	        try {
				xmlString = EntityUtils.toString(entity);
				
				logFinest("login xml: " + xmlString);

				if (j == 0) {
					token = parseXMLforInfo("token", xmlString, "\"");
					
					logFinest("Login token: " + token);
				}
			} catch (org.apache.http.ParseException | IOException e) {
				e.printStackTrace();
			}
        }
        
        boolean success = xmlString.contains("Success");
		logInfo("Login status at " + language + ": " + success);
        
		if (success) {
			loggedInAtLanguages.add(language);
		}
		
        return success;
	}
	
	public String APIcommand(APIcommand command) {
		//Check throttle.
		throttleAction();
		
		//Do the command!
		baseURL = mdm.getWikiURL(command.getPageLocation().getLanguage());
		
		String textReturned;
		if (command.requiresGET()) {
			textReturned = APIcommandGET(command);
		} else {
			textReturned = APIcommandHTTP(command);
		}
		logFinest("API results obtained.");

		//Handle mediawiki output.
		if (textReturned != null) {
			if (textReturned.contains("<!DOCTYPE HTML>") || textReturned.contains("<!DOCTYPE html>")) {
				//We are handling HTML output.
				//We will parse it for any errors/warnings.
				
				//Unescape html
				textReturned = StringEscapeUtils.unescapeHtml4(StringEscapeUtils.unescapeHtml4(textReturned));
				
				if (textReturned.contains("This is an auto-generated MediaWiki API documentation page")) {
					//You got the Mediawiki API documentation sent back.
					logFinest("MediawikiAPI documentation page returned.");
					
					String error = parseXMLforInfo("error code", textReturned, "\"");
					if (textReturned.contains("info=")) {
						error += ":" + parseXMLforInfo("info", textReturned, "\"");
					}
					logError(error);
					throw new Error(error);
				} else {
					//Log a small portion of the html. It's nice.
					if (textReturned.length() < 1000) {
						logFinest("HTML: " + textReturned);
					} else {
						logFinest("HTML: " + textReturned.substring(0, 1000));	
					}

					if (textReturned.contains("<warnings>")) {
						logError("Warnings were recieved when editing " + command.getTitle() + ".");
					} else {
						//Check other possibilities for errors/warnings being returned..
						String errorMessage = null;
						if (textReturned.contains("\"warning")) {
							errorMessage = "\"warning";
						}
						if (textReturned.contains("\"error")) {
							errorMessage = "\"error";
						}
						
						if (errorMessage != null) {
							//Errors/warnings detected.
							String xmlSnippet = parseXMLforInfo(errorMessage, textReturned, "}");
							String error = "";
							
							for (int i = 0, prevI = 0; i != -1; prevI = i, i = xmlSnippet.indexOf("\n", i+1)) {
								if (prevI != 0) {
									String temp = xmlSnippet.substring(prevI, i);
									temp = temp.replace("\n", "");
									temp = temp.trim();
									error += temp + " | ";
								}
							}
							logError(error);
						} else {
							//Everything looks ok.
							logFinest(command.getTitle() + " has been edited.");
						}
					}
				}
			} else if (textReturned.contains("<?xml version")) {
				//We are handling XML output. We do not do anything.
				logFinest("XML recieved.");
				if (textReturned.length() < 1000) {
					logFinest("XML: " + textReturned);
				} else {
					logFinest("XML: " + textReturned.substring(0, 1000));	
				}
			} else {
				//We are handling JSON output.
				//We will look for errors/warnings.
				
				//Error handling
				if (textReturned.contains("This is an auto-generated MediaWiki API documentation page")) {
					//Ugh, the Mediawiki API documentation was returned.
					logFinest("Mediawiki API documentation page returned.");
					
					String error = "||" + parseXMLforInfo("code", textReturned, "\",", 3, 0);
					if (textReturned.contains("info")) {
						error += ":" + parseXMLforInfo("info", textReturned, "\",", 3, 0);
					}
					logError(error);
					throw new Error(error);
				} else {
					//Log a small portion of the JSON. It's nice.
					if (textReturned.length() < 1000) {
						logFinest("JSON: " + textReturned);
					} else {
						logFinest("JSON: " + textReturned.substring(0, 1000));	
					}
					//log("JSON: " + textReturned);
					
					//Look for errors/warnings.
					if (textReturned.contains("\"error\"")) {
						logError(parseXMLforInfo("\"info\"", textReturned, "\","));
					} else if (textReturned.contains("Internal Server Error")){
						logError("Internal Server Error");
						logFinest(textReturned);
						throw new Error("Internal Server Error");
					} else {
				        logFinest("Down with page " + command.getPageLocation().getTitle() + ".");
					}
				}
			}
		}
		
		return textReturned;
	}

	/**
	 * Automatically unescapes HTML5.
	 */
	private String APIcommandHTTP(APIcommand command) {
		String url = baseURL + "/api.php?";
		String[] editKeys = command.getKeysArray();
		String[] editValues = command.getValuesArray();
		
		for (int i = 0; i < editKeys.length; i++) {
			url += URLencode(editKeys[i]) + "=" + URLencode(editValues[i]);
			if (i != editKeys.length-1) {
				url += "&";
			}
		}
		
		try {
			String[] output = getURL(url, command.shouldUnescapeText(), command.shouldUnescapeHTML());
			if (output == null) {
				throw new NetworkError("Cannot connect to server at: " + baseURL);
			} else {
				return compactArray(output, "\n");
			}
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	private String APIcommandGET(APIcommand command) {
		HttpEntity entity;
		
		String token = getEditToken();
		
		String[] editKeys;
		String[] editValues;
		editKeys = command.getKeysArray();
		editValues = command.getValuesArray();
		
		//Get command parameters
		String[] keys = new String[editKeys.length + 1];
		String[] values = new String[editValues.length + 1];
		
		//Shift parameters to larger array
		for (int i = 0; i < editKeys.length; i++) {
			keys[i] = editKeys[i];
			values[i] = editValues[i];
		}
		
		//Add a few more parameters
		keys[keys.length-1] = "token";
		values[keys.length-1] = "" + token;

		//Send the command!
        entity = getPOST(baseURL + "/api.php?", keys, values);
        try {
			String xmlString = EntityUtils.toString(entity);
			
			return xmlString;
		} catch (org.apache.http.ParseException | IOException e) {
			e.printStackTrace();
			
			return null;
		}
	}
	
	protected String getEditToken() {
		String[] keys = new String[]{"action", "type", "format"};
		String[] values = null;
		values = new String[]{"tokens", "edit", "xml"};
        
        HttpEntity entity = getPOST(baseURL + "/api.php?", keys, values);
        
		String xmlString = "";
		String token = "";
		try {
			xmlString = EntityUtils.toString(entity);
			token = parseXMLforInfo("edittoken", xmlString, "\"");
		} catch (org.apache.http.ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return token;
	}
	
	/**
	 * Read in a text file.
	 * @param location The location of the file.
	 * @param commentBufferLineCount How many lines to ignore at the beginning of the file.
	 * @param hasComments If true, a line that starts with # is considered a comment, and hence is ignored.
	 * @param ignoreBlankLines If true, blank lines are ignored.
	 * @return The text file.
	 */
	public ArrayList<String> readFileAsList(String location, int commentBufferLineCount, boolean hasComments, boolean ignoreBlankLines) {
		try {
			// Read in the file!
			InputStream in = getClass().getResourceAsStream(location);
			BufferedReader br = new BufferedReader(
						new InputStreamReader(in)
					);
			
			// Ignore the comment
			for (int i = 0; i < commentBufferLineCount; i++) {
				br.readLine();
			}
			
			// Gather array size
			ArrayList<String> lines = new ArrayList<String>();
			
			// Parse file array into java int array
			String line;
			line = br.readLine();
			do {
				if (hasComments && (line.length() > 0 && line.substring(0,1).equals("#"))) {
					//We have a comment. Ignore it.
				} else if (ignoreBlankLines && line.length() == 0) {
					//We have an empty line.
				} else {
					lines.add(line);
				}
				line = br.readLine();
			} while (line != null);
			
			in.close();
			br.close();
			
			return lines;
			
		} catch (IOException e) {
			logError("Error reading in list at: " + location);
		}
		return null;
	}
	
	/*
	 * <notice>
	 * 
	 * 
	 * The code below is some minor class code. Unless you are an advanced user, you can ignore it.
	 * 
	 * 
	 * </notice>
	 */
	
	/*
	 * This is a specialized function and should not be used outside of this class.
	 */
	protected ArrayList<String> getPages(String XMLdata, String openingText, String closingText) {
		//This method takes XMLdata and parses it for page names.
		ArrayList<String> output = new ArrayList<String>();
		int j = 0;
		int k = -1;
		String temp;
		//Parse page for info.
		do {
			j = XMLdata.indexOf(openingText, k+1);
			k = XMLdata.indexOf(closingText, j+1);
			if (j != -1) {
				//No errors detected.
				temp = XMLdata.substring(j, k+6);
				output.add(parseXMLforInfo("title", temp, "\""));
			}
		} while(j != -1);
		return output;
	}
	
	/*
	 * This is a specialized function and should not be used outside of this class.
	 */
	private ArrayList<Revision> getRevisionsFromXML(String XMLdata, String openingText, String closingText, boolean includeContent, String forceTitle) {
		//This method takes XML data and parses it for revisions.
		
		//Revision related data.
		ArrayList<Revision> output = new ArrayList<Revision>();
		String revision;
		String user;
		String comment;
		String tempDate;
		Date date = null;
		String content;
		String title;
		ArrayList<String> flags;
		
		//Iteration variables.
		int j = 0;
		int k = -1;
		while (j != -1) {
			j = XMLdata.indexOf(openingText, k+1);
			k = XMLdata.indexOf(closingText, j+1);
			if (j != -1) {
				//No errors detected. Continue parsing.
				
				//Get a single revision's text.
				revision = XMLdata.substring(j, k+closingText.length());
				
				//Extract info.
				user = parseXMLforInfo("user", revision, "\"", 2, 0);
				tempDate = parseXMLforInfo("timestamp", revision, "\"", 2, 0);
				date = createDate(tempDate);
				content = null;
				if (includeContent) {
					comment = parseXMLforInfo("comment", revision, "\" contentformat", 2, 0);
					content = parseXMLforInfo("xml:space=\"preserve\"", revision, "</rev>", 1, 0);
				} else {
					comment = parseXMLforInfo("comment", revision, closingText, 2, 0);
				}
				
				//Parse for flags
				flags = new ArrayList<String>();
				ArrayList<String> flagsToSearchFor = new ArrayList<String>();
				flagsToSearchFor.add("minor");
				flagsToSearchFor.add("new");
				flagsToSearchFor.add("bot");
				for (String flag: flagsToSearchFor) {
					if (revision.contains(flag + "=\"\"")) {
						//Flag found.
						flags.add(flag);
					}
				}
				
				//Generate and store revision
				Revision rev;
				if (forceTitle == null) {
					title = parseXMLforInfo("title", revision, "\"");
					rev = new Revision(new PageLocation(title, mdm.getLanguageFromURL(baseURL)), user, comment, date, flags);
				} else {
					rev = new Revision(new PageLocation(forceTitle, mdm.getLanguageFromURL(baseURL)), user, comment, date, flags);
				}
				
				rev.setPageContent(content);
				
				//For eventual return.
				output.add(rev);
			}
		}
		return output;
	}
	
	protected ArrayList<String> getXMLItems(String XMLdata, String openingText, String closingText, int botBuffer) {
		//This method takes XMLdata and parses it for page names.
		ArrayList<String> output = new ArrayList<String>();
		int j = 0;
		int k = -1;

		//Parse page for info.
		do {
			j = XMLdata.indexOf(openingText, k+1);
			k = XMLdata.indexOf(closingText, j+1);
			if (j != -1) {
				//No errors detected.
				output.add(XMLdata.substring(j+botBuffer, k));
			}
		} while(j != -1);
		return output;
	}
	
	protected String parseXMLforInfo(String info, String XMLcode, String ending) {
		//This method aids in XML parsing.
		int i = XMLcode.indexOf(info);
		if (i == -1) {
			throw new IndexOutOfBoundsException();
		}
		i += info.length() + 2;
		int j = XMLcode.indexOf(ending, i+1);
		if (j != -1) {
			return XMLcode.substring(i,  j);
		} else {
			return null;
		}
	}
	
	protected String parseXMLforInfo(String info, String XMLcode, String ending, int bufferBot, int bufferTop) {
		//This method aids in XML parsing.
		int i = 0;
		i = XMLcode.indexOf(info);
		i += info.length() + bufferBot;
		return XMLcode.substring(i, XMLcode.indexOf(ending, i) - bufferTop);
	}
	
	/**
	 * This takes a String date and converts it into a Date object.
	 * @param text A String representing a date.
	 * @return A Date object.
	 */
	public Date createDate(String text) {
		Date date = null;
		try {
			date = dateFormat.parse(text);
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}
		return date;
	}
	
	/**
	 * This method makes sure that the bot does not do particular actions too quickly.
	 */
	private void throttleAction() {
		long currentTime = System.currentTimeMillis();
		long timeDifference = currentTime - lastCommandTimestamp;
		long timeToWait = (long) (1000*APIthrottle - timeDifference);
		if (timeToWait > 0) {
			sleep(timeToWait);
		}
		lastCommandTimestamp = System.currentTimeMillis();
	}
	
	public void sleepInSeconds(double time) {
		try {
			Thread.sleep((int)(1000*time));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public void sleep(long time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public boolean shouldParseThurough() { return parseThurough; }
	public String getHomeWikiLanguage() { return homeWikiLanguage; }
}
