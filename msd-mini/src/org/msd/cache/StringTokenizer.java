package org.msd.cache;

/** This class tries to replace the java.util.StringTokenizer one, not
* provided for J2ME (MIDP2). It is a simple implementation without the power
* of the original, but enough for our purposes.
*
* For example, new StringTokenizer("hello world"," ");
* will return "hello", "world" and null in nextToken();
* new StringTokenizer("hello-bye-there","-bye-"); will
* return the same (compare with the behaviour of the original Tokenizer)
* @version $Revision: 1.3 $
*/
public class StringTokenizer{
        private int p=0;
        private String sep=null;
        private String s;
        private boolean end=false;
        /** Constructor.
         * @param s The string to be tokenized.
         * @param sep The string separing the tokens of s */
        public StringTokenizer(String s,String sep){
            this.s=s;
            this.sep=sep;
            p=0;
        }

        /** @return A new token of the string, or null */
        public String nextToken(){
            if(p>=s.length()||p<0){
                end=true;
                return null;
            }
            int a=p;
            String next=null;
            p=s.indexOf(sep,p);
            if(p==0){
                p=p+sep.length();
                return nextToken();
            }
            if(p==-1){
                next=s.substring(a,s.length());
                end=true;
            } else{
                next=s.substring(a,p);
                p=p+sep.length();
            }
            return next;
        }

        /** @return Wether the string has more tokens awaiting */
        public boolean hasMoreTokens(){
            return !end;
        }
    }
