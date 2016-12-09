import java.util.PriorityQueue;

/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;

	public enum Header{TREE_HEADER, COUNT_HEADER};
	public Header myHeader = Header.TREE_HEADER;
	
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
	    TreeNode root = makeTreeFromCounts(counts);
	    String[] codings = makeCodingsFromTree(root, "");
	    writeHeader(root,out);
	 
	    in.reset();


	    writeCompressedBits(in,codings, out); 

	}

	public int[] readForCounts(BitInputStream in){
		int[] ret = new int[256];
		int val = in.readBits(BITS_PER_WORD);    //can this be in first line of while loop?
		while (true){
			if (val == -1) break;
			ret[val] += 1;
			val = in.readBits(BITS_PER_WORD);
		}
		return ret;
	}

	public TreeNode makeTreeFromCounts( int[] ret){
		PriorityQueue<TreeNode> pq = new PriorityQueue<>();
		for (int k =0; k<ret.length; k++){
			if (ret[k]>0){
				TreeNode leaf = new TreeNode(k, ret[k]);
				pq.add(leaf);
			}
		}
	
		// call pq.add(new TreeNode(...)) for every 8-bit
		
		pq.add(new TreeNode(PSEUDO_EOF, 1));
		
		// value that occur one or more times, including PSEUDO_EOF!!!

		while (pq.size() > 1) {
		    TreeNode left = pq.remove();
		    TreeNode right = pq.remove();
		    TreeNode t = new TreeNode(-1,
		                 left.myWeight + right.myWeight,
		                 left,right);
		    pq.add(t);
		}
		TreeNode root = pq.remove();
		return root;
	}
	
	public String[] makeCodingsFromTree(TreeNode tree, String code){
		String[] codeArray = new String[257];
		return helpFromTree(tree, code, codeArray);
		
	}
	public String[] helpFromTree(TreeNode tree, String code, String[] help){	
		
		if (tree.myLeft != null){
			if (tree.myRight != null){
				helpFromTree(tree.myLeft, code+"0", help);
				helpFromTree(tree.myRight, code+"1", help);
			}
		}
		else if (tree.myLeft ==null && tree.myRight ==null){
			help[tree.myValue] = code;
		}
		return help;
	}
	
	public void writeHeader(TreeNode tree, BitOutputStream out){
		out.writeBits(BITS_PER_INT, HUFF_NUMBER);
		
		if(tree != null) helpheader(tree, out);
		
	}
	public void helpheader(TreeNode tree, BitOutputStream out){
		//need recursion
		if(tree != null){
			if(tree.myLeft ==null && tree.myRight == null){
				out.writeBits(1, 1);
				out.writeBits(9, tree.myValue);
			}
			else{
				out.writeBits(1, 0);
				helpheader(tree.myLeft, out);
				helpheader(tree.myRight, out);
			}
		}
	}
	
	public void writeCompressedBits(BitInputStream in, String[] codings, BitOutputStream out){
		int val_bit = in.readBits(BITS_PER_WORD);
		
		while (true){
			if (val_bit == -1) break;
			String val_string = codings[val_bit];
			//System.out.println(val_string);
			out.writeBits(val_string.length(), Integer.parseInt(val_string, 2));
			val_bit = in.readBits(BITS_PER_WORD);
		}
		String PseudoString = codings[PSEUDO_EOF];
		out.writeBits(PseudoString.length(), Integer.parseInt(PseudoString, 2));
	}
	
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int id = in.readBits(BITS_PER_INT);
		if ((id!= HUFF_TREE) && (id != HUFF_NUMBER)) throw new HuffException("BITS_PER_INT is not in vaild BIT");
		TreeNode root = readTreeHeader(in);
	    readCompressedBits(in,out, root);

        
	}
	public TreeNode readTreeHeader(BitInputStream in){
		int nextBit = in.readBits(1);
		TreeNode root = new TreeNode(0,0);
		if (nextBit == 1){
			int treeval = in.readBits(9);
			root = new TreeNode(treeval, 0);
			
		}
		else if( nextBit == 0){
			
			root.myLeft = readTreeHeader(in);
			root.myRight = readTreeHeader(in);
		}
		return root;
	}
	public void readCompressedBits(BitInputStream in, BitOutputStream out,TreeNode tree){
		TreeNode currNode = tree;
		
		while(true){
			int val = in.readBits(1);
			
			
			if(val == -1) throw new HuffException("Invalid input");
			if(val ==0){
				TreeNode temp = currNode.myLeft;
				currNode = temp;
			}
			if (val ==1){
				currNode = currNode.myRight;
			}
			if(currNode.myLeft == null ){
				if(currNode.myRight == null){
					if(currNode.myValue == PSEUDO_EOF) break;
					out.writeBits(BITS_PER_WORD, currNode.myValue);
					currNode = tree;
				}
			}
			
			
		}
		

	}
	
	public void setHeader(Header header) {
        myHeader = header;
        System.out.println("header set to "+myHeader);
    }
}