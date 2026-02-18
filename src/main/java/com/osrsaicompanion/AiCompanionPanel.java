package com.osrsaicompanion;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;

public class AiCompanionPanel extends PluginPanel
{
	private final OsrsAiCompanionPlugin plugin;

	private JTextPane chatArea;
	private JScrollPane scrollPane;
	private JTextField inputField;
	private JButton sendButton;
	private Element thinkingElement;
	private JTextArea goalArea;

	private static final String INITIAL_HTML = "<html><body id='body'></body></html>";

	private static final String CSS =
		"body { background-color: #282828; color: #c8c8c8; font-family: sans-serif; font-size: 12px; margin: 4px; }" +
		".user { color: #64b4ff; font-weight: bold; text-align: right; margin: 4px 0; }" +
		".claude { color: #c8c8c8; margin: 4px 0; }" +
		".label { font-size: 10px; }" +
		".thinking { color: #888888; font-style: italic; }" +
		".error { color: #ff4444; margin: 4px 0; }" +
		".event { color: #64b4ff; font-weight: bold; margin: 4px 0; }";

	public AiCompanionPanel(OsrsAiCompanionPlugin plugin)
	{
		super(false);
		this.plugin = plugin;
		buildUI();
	}

	private void buildUI()
	{
		setLayout(new BorderLayout());
		setBackground(new Color(0x28, 0x28, 0x28));

		// NORTH: title bar
		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBackground(new Color(0x28, 0x28, 0x28));
		topBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JLabel titleLabel = new JLabel("AI Companion");
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
		titleLabel.setForeground(new Color(0xc8, 0xc8, 0xc8));

		JButton clearButton = new JButton("Clear");
		clearButton.setFocusPainted(false);
		clearButton.addActionListener((ActionEvent e) -> {
			plugin.clearHistory();
			chatArea.setText(INITIAL_HTML);
		});

		topBar.add(titleLabel, BorderLayout.CENTER);
		topBar.add(clearButton, BorderLayout.EAST);

		// Goal section
		JPanel goalPanel = new JPanel(new BorderLayout());
		goalPanel.setBackground(new Color(0x28, 0x28, 0x28));
		goalPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));

		JLabel goalLabel = new JLabel("Your Goal");
		goalLabel.setFont(goalLabel.getFont().deriveFont(Font.BOLD, 11f));
		goalLabel.setForeground(new Color(0x88, 0x88, 0x88));
		goalLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

		goalArea = new JTextArea(3, 0);
		goalArea.setLineWrap(true);
		goalArea.setWrapStyleWord(true);
		goalArea.setBackground(new Color(0x1e, 0x1e, 0x1e));
		goalArea.setForeground(new Color(0xc8, 0xc8, 0xc8));
		goalArea.setCaretColor(new Color(0xc8, 0xc8, 0xc8));
		goalArea.setFont(goalArea.getFont().deriveFont(11f));
		goalArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		goalArea.setText(plugin.getGoal());

		JButton saveGoalButton = new JButton("Save");
		saveGoalButton.setFocusPainted(false);
		saveGoalButton.addActionListener((ActionEvent e) -> plugin.saveGoal(goalArea.getText().trim()));

		goalPanel.add(goalLabel, BorderLayout.NORTH);
		goalPanel.add(new JScrollPane(goalArea), BorderLayout.CENTER);
		goalPanel.add(saveGoalButton, BorderLayout.SOUTH);

		JPanel northWrapper = new JPanel(new BorderLayout());
		northWrapper.setBackground(new Color(0x28, 0x28, 0x28));
		northWrapper.add(topBar, BorderLayout.NORTH);
		northWrapper.add(goalPanel, BorderLayout.CENTER);
		add(northWrapper, BorderLayout.NORTH);

		// CENTER: chat area
		chatArea = new JTextPane();
		chatArea.setContentType("text/html");
		chatArea.setEditable(false);
		chatArea.setBackground(new Color(0x28, 0x28, 0x28));

		HTMLEditorKit editorKit = new HTMLEditorKit();
		chatArea.setEditorKit(editorKit);

		javax.swing.text.html.StyleSheet styleSheet = editorKit.getStyleSheet();
		styleSheet.addRule(CSS);

		chatArea.setText(INITIAL_HTML);

		scrollPane = new JScrollPane(chatArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(null);
		add(scrollPane, BorderLayout.CENTER);

		// SOUTH: input row
		JPanel inputPanel = new JPanel(new BorderLayout());
		inputPanel.setBackground(new Color(0x28, 0x28, 0x28));
		inputPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		inputField = new JTextField();
		inputField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					onSendClicked();
				}
			}
		});

		sendButton = new JButton("Send");
		sendButton.setFocusPainted(false);
		sendButton.addActionListener((ActionEvent e) -> onSendClicked());

		inputPanel.add(inputField, BorderLayout.CENTER);
		inputPanel.add(sendButton, BorderLayout.EAST);
		add(inputPanel, BorderLayout.SOUTH);
	}

	private void onSendClicked()
	{
		String text = inputField.getText().trim();
		if (text.isEmpty())
		{
			return;
		}
		inputField.setText("");
		setInputEnabled(false);
		plugin.sendMessage(text);
	}

	public void appendUserMessage(String text)
	{
		String escaped = escapeHtml(text);
		appendHtml("<div class='user'><span class='label'>You</span><br>" + escaped + "</div>");
	}

	public void appendClaudeMessage(String text)
	{
		removeThinkingIndicator();
		String stripped = stripEmoji(text);
		String escaped = escapeHtml(stripped).replace("\n", "<br>");
		appendHtml("<div class='claude'><span class='label'>Claude</span><br>" + escaped + "</div>");
		setInputEnabled(true);
	}

	public void appendErrorMessage(String text)
	{
		removeThinkingIndicator();
		String escaped = escapeHtml(text);
		appendHtml("<div class='error'>" + escaped + "</div>");
		setInputEnabled(true);
	}

	public void appendThinkingIndicator()
	{
		appendHtml("<div id='thinking' class='thinking'>Claude is thinking...</div>");
		thinkingElement = ((HTMLDocument) chatArea.getDocument()).getElement("thinking");
	}

	public void appendEventMessage(String text)
	{
		String escaped = escapeHtml(text);
		appendHtml("<div class='event'>" + escaped + "</div>");
	}

	private void removeThinkingIndicator()
	{
		if (thinkingElement != null)
		{
			((HTMLDocument) chatArea.getDocument()).removeElement(thinkingElement);
			thinkingElement = null;
		}
	}

	public void setInputEnabled(boolean enabled)
	{
		inputField.setEnabled(enabled);
		sendButton.setEnabled(enabled);
	}

	private void appendHtml(String html)
	{
		HTMLEditorKit kit = (HTMLEditorKit) chatArea.getEditorKit();
		HTMLDocument doc = (HTMLDocument) chatArea.getDocument();
		try
		{
			kit.insertHTML(doc, doc.getLength(), html, 0, 0, null);
		}
		catch (BadLocationException | IOException e)
		{
			// ignore render errors
		}
		SwingUtilities.invokeLater(() ->
			scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum())
		);
	}

	private static String stripEmoji(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replaceAll("[\\x{1F000}-\\x{1FFFF}]|[\\x{2600}-\\x{27FF}]|[\\x{2300}-\\x{23FF}]|\\x{FE0F}", "").trim();
	}

	private static String escapeHtml(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;");
	}
}
