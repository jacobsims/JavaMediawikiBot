package WikiBot.Content;

import java.util.ArrayList;

public class Template extends PageObjectAdvanced {
	
	String templateName;
	
	public Template(int openPos_, int closePos_, String pageTitle_, String rawTemplateText_, ArrayList<PageObject> pageObjects_, ArrayList<String> parameters_) {
		super("Template", rawTemplateText_, openPos_, closePos_, parameters_, pageObjects_, "{{", "}}");
		setTemplateName(pageTitle_, rawTemplateText_);
	}
	
	public Template(int openPos_, int closePos_, String pageTitle_, String rawTemplateText_) {
		super("Template", rawTemplateText_, openPos_, closePos_, "{{", "}}");
		setTemplateName(pageTitle_, rawTemplateText_);
	}
	
	private void setTemplateName(String pageTitle_, String rawTemplateText_) {
		if (header.length() >= 1 & header.substring(0,1).equals("/")) {
			templateName = pageTitle_ + rawTemplateText_;
		} else if (header.length() >= 1 & header.substring(0,1).equals(":")) {
			templateName = rawTemplateText_.substring(1);
		} else if (header.length() >= 9 && header.substring(0,9).equalsIgnoreCase("Template")) {
			templateName = rawTemplateText_;
		} else {
			templateName = "Template:" + header;
		}
	}
	
	public String getTemplateName() {
		return templateName;
	}
	
	public String simpleToString() {
		return "(Template) Name: "  + header + " (at: " + openPos + ")";
	}
	
	@Override
	public String toString() {
		String output;

		output = simpleToString();
		output += "\nWith parameters: ";
		for (int i = 0; i < parameters.size(); i++) {
			output += (parameters.get(i) + " , ");
		}
		output += "\nWith pageObjects: ";
		for (int i = 0; i < pageObjects.size(); i++) {
			output += (pageObjects.get(i).simpleToString() + " , ");
		}

		return output;
	}
}