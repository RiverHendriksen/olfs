package opendap.metacat;

import java.util.Enumeration;
import java.util.Vector;

public class URLProcessedComponents {
	public class Lexeme {
		private String value;
		private boolean pattern;
		
		public Lexeme() {
			value = "";
			pattern = false;
		}
		public boolean isPattern() { return pattern; }
		public String getValue() { return value; }
	}

	private Vector<Lexeme> theClasses;
	
	// Testing only...
	
	public static void main(String args[]) {
		if (args.length < 1) return;
		
		try {
			URLProcessedComponents pc = new URLProcessedComponents(args[0]);

			System.out.println(args[0]); // print URL

			String classes[] = pc.getLexemeArray();
			for (String cls : classes)
				System.out.print(cls + " ");
			System.out.println();
			
			Lexemes ce = pc.getLexemes();
			while (ce.hasMoreElements()) {
				Lexeme c = ce.nextElement();
				if (c.isPattern())
					System.out.print(c.getValue() + ": pattern ");
				else
					System.out.print(c.getValue() + " ");
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public URLProcessedComponents(ParsedURL url) {
		buildEquivalenceClasses(url);
	}
	
	public URLProcessedComponents(String url) throws Exception {
		buildEquivalenceClasses(new ParsedURL(url));
	}
	
	/**
	 * This is the simple code to build the equivalences. 
	 * 
	 * @param url An instance of URLParser
	 */
	private void buildEquivalenceClasses(ParsedURL url) {
		String[] comps = url.getComponents();
		
		theClasses = new Vector<Lexeme>();
		
		Lexeme previousLexeme = null;
		for(String comp: comps) {
			Lexeme c = new Lexeme();
			
			// Rule: if comp is all digits, replace each with 'd'
			if (comp.matches("[0-9]+")) {
				int j = 0;
				while (j++ < comp.length())
					c.value += 'd';
				// Hack: if the previous lexeme was 'dddd' and this one is 'd'
				// make it 'dd' because it's likely we have a degenerate case
				// where months are represented using both one and two digit
				// values.
				if (previousLexeme != null && previousLexeme.value.equals("dddd") && c.value.equals("d"))
					c.value += 'd';
					
				c.pattern = true;
			}
			// if comp is a string of digits followed by chars, replace each
			// digit by a 'd' but keep the literal char data. Allow for a
			// trailing sequence of digits to follow the char data, but treat
			// those as literals. Note that there are plenty of cases where
			// a single digit starts out a literal so require at least two 
			// digits at the front.
			else if (comp.matches("[0-9][0-9]+[A-Za-z]+[0-9]*")) {
				int j = 0;
				while (j < comp.length() && Character.isDigit(comp.charAt(j))) {
					c.value += 'd';
					++j;
				}
				while(j < comp.length())
					c.value += comp.charAt(j++);

				c.pattern = true;
			}
			// If comp is a sequence of chars followed by a sequence of digits,
			// replace the digits by 'd'.
			else if (comp.matches("[A-Za-z]+[0-9]+")) {
				int j = 0;
				while (j < comp.length() && Character.isLetter(comp.charAt(j)))
					c.value += comp.charAt(j++);
				while(j < comp.length()) {
					c.value += 'd';
					++j;
				}

				c.pattern = true;
			}
			else {
				c.value = comp;
				c.pattern = false;
			}
			
			theClasses.add(c);
			previousLexeme = c;
		}
	}
	
	public class Lexemes implements Enumeration<Lexeme> {
		private Enumeration<Lexeme> e = theClasses.elements();
		@Override
		public boolean hasMoreElements() {
			return e.hasMoreElements();
		}

		@Override
		public Lexeme nextElement() {
			return e.nextElement();		
		}
	}
	
	public Lexemes getLexemes() {
		return new Lexemes();
	}
	
	public String[] getLexemeArray() {
		String[] result = new String[theClasses.size()];
		int i = 0;
		for (Lexeme c: theClasses) 
			result[i++] = c.value;
		return result;
	}
}
