/**
 * Base Text editor.
 *
 * Not an editor itself, it just introduces functions common for all text based editors - that is common reaction
 * to F2 F3 keys.
 *
 * @requires Prototype v1.6.1+ library
 *
 * @author Andrey Naumenko
 */

var BaseTextEditor = Class.create(BaseEditor, {

    maxInputSize: null,

    selectedIndex: null,

    inputTimeout: null,

    createInput: function () {
        this.input = new Element("input");
        this.input.setAttribute("type", "text");
        if (this.maxInputSize) {
            this.input.maxLength = this.maxInputSize;
        }

        this.setDefaultStyle();

        this.input.setStyle(this.style);
        this.input.setAttribute("autocomplete", "off"); // Disable autocomplete

        var autocompleteOptions = [];
        var self = this; // Store reference to 'this'//

        function loadOptions(inputValue, cb) {
            // Make an AJAX request to the server
            var xhr = new XMLHttpRequest();
            var serverEndpoint = self.tableEditor.baseUrl;
            var row = self.tableEditor.selectionPos[0];
            var col = self.tableEditor.selectionPos[1];
            var context;
            if (serverEndpoint.startsWith('/')) {
                context = serverEndpoint.split("/")[1];
            } else {
                context = serverEndpoint.split("/")[0];
            }
            xhr.open('GET', "/" + context + "/web/tableEditor/typeahead?row=" + row + "&col=" + col + "&text=" + btoa(inputValue), false);

            xhr.onload = function () {
                if (xhr.status === 200) {
                    // Parse the JSON response, extract the relevant data and fill the autocompleteOptions array
                    autocompleteOptions = JSON.parse(xhr.responseText);
                    cb();
                }
            };

            xhr.onerror = function () {
                // Handle error
            };

            xhr.send();
        }

        function createOrGetAutocompleteList() {
            var autocompleteList = document.getElementById("autocomplete-list");
            // Create a new list to hold matching options if it doesn't exist
            if (!autocompleteList) {
                autocompleteList = document.createElement("div");
                autocompleteList.setAttribute("id", "autocomplete-list");
                self.input.parentNode.appendChild(autocompleteList);
                // Set the style properties
                autocompleteList.style.position = "absolute";
                autocompleteList.style.border = "1px solid #ccc";
                autocompleteList.style.backgroundColor = "#f9f9f9";
                autocompleteList.style.overflowY = "auto";
                autocompleteList.style.whiteSpace = "nowrap";
            }
            return autocompleteList;
        }

        function crossLen(inputValue, value) {
            var k = 0;
            for (var i = 1; i <= inputValue.length; i++) {
                if (i < value.length) {
                    var p1 = value.substring(0, i);
                    var p2 = inputValue.substring(inputValue.length - i);
                    if (p1 === p2 && k < i) {
                        k = i;
                    }
                }
            }
            return k;
        }

        this.input.addEventListener("input", function () {
            clearTimeout(self.inputTimeout); // Clear the previous timeout
            self.inputTimeout = setTimeout(function () {
                var inputValue = this.value;

                loadOptions(inputValue, function () {
                    self.closeAutocompleteList();
                    var autocompleteList = createOrGetAutocompleteList();
                    // Create a new list to hold matching options
                    var matchingOptions = document.createElement("div");
                    matchingOptions.setAttribute("id", "autocomplete-options");
                    autocompleteList.appendChild(matchingOptions);

                    // Find matching options from the autocompleteOptions list
                    var matchingValues = autocompleteOptions.filter(function (option) {
                        return crossLen(inputValue, option) > 0;
                    });

                    // Create and append option elements to the list
                    matchingValues.forEach(function (value) {
                        var option = document.createElement("div");
                        if (inputValue) {
                            var k = crossLen(inputValue, value);
                            var s = value.substr(0, k);
                            option.innerHTML = "<strong>" + s + "</strong>" + value.substr(s.length);
                        }
                        option.addEventListener("click", function () {
                            self.input.value = value;
                            self.closeAutocompleteList();
                        });
                        matchingOptions.appendChild(option);
                    });
                    // Show the autocomplete list
                    autocompleteList.style.display = "block";
                });
            }.bind(this), 1500);
        });

        this.selectedIndex = -1; // Track the currently selected index

        function selectItem(index) {
            var autocompleteList = createOrGetAutocompleteList();
            var options = autocompleteList.querySelectorAll("div:not(#autocomplete-options)");
            if (index < 0) {
                index = options.length - 1;
            } else if (index >= options.length) {
                index = 0;
            }
            self.selectedIndex = index;
            options.forEach(function (option, i) {
                if (i === self.selectedIndex) {
                    option.classList.add("selected");
                    option.style.backgroundColor = "#ccc"; // Add your desired styling
                    option.style.color = "#fff"; // Add your desired styling
                } else {
                    option.classList.remove("selected");
                    option.style.backgroundColor = ""; // Reset styling
                    option.style.color = ""; // Reset styling
                }
                //option.classList.toggle("selected", i === selectedIndex);
            });
        }

        // Handle keydown events
        this.input.addEventListener("keydown", function (event) {
            if (event.key === "ArrowUp") {
                event.preventDefault();
                selectItem(self.selectedIndex - 1);
            } else if (event.key === "ArrowDown") {
                event.preventDefault();
                selectItem(self.selectedIndex + 1);
            } else if (event.key === "Enter") {
                event.preventDefault();
                var options = createOrGetAutocompleteList().querySelectorAll("div:not(#autocomplete-options)");
                if (self.selectedIndex >= 0 && self.selectedIndex < options.length) {
                    var p = options[self.selectedIndex].querySelector("strong").textContent
                    var s = options[self.selectedIndex].textContent
                    self.input.value = self.input.value + s.substring(p.length)
                    self.closeAutocompleteList();
                }
            }
        });

        // Event listener for document click
        document.addEventListener("click", function (event) {
            if (event.target !== self.input) {
                self.closeAutocompleteList();
            }
        });

    },

    setDefaultStyle: function () {
        this.input.style.border = "1px solid threedface";
        this.input.style.margin = "0px";
        //this.input.style.padding = "0px";
        this.input.style.width = "100%";
        this.input.style.height = "100%";
    },

    /**
     * Moves caret to beginning of the input.
     */
    handleF2: function (event) {
        var input = this.getInputElement();
        if (input.createTextRange) {
            var r = input.createTextRange();
            r.collapse(true);
            r.select();

        } else if (input.setSelectionRange) {
            input.setSelectionRange(0, 0);
            this.focus();
        }
        Event.stop(event);
    },

    /**
     * Moves caret to the end of the input.
     */
    handleF3: function (event) {
        var input = this.getInputElement();
        if (!input) return;
        if (input.createTextRange) {
            var r = input.createTextRange();
            r.collapse(false);
            r.select();

        } else if (input.setSelectionRange) {
            var len = input.value.length;
            input.setSelectionRange(len, len);
            this.focus();
        }

        if (event) Event.stop(event);
    },

    // Function to close the autocomplete list
    closeAutocompleteList: function closeAutocompleteList() {
        this.selectedIndex = -1;
        var autocompleteList = document.getElementById("autocomplete-list");
        if (autocompleteList) {
            while (autocompleteList.firstChild) {
                autocompleteList.removeChild(autocompleteList.firstChild);
            }
            autocompleteList.style.display = "none";
        }
    },

    show: function ($super, value) {
        $super(value);
        if (this.focussed) {
            this.handleF3();
        }
    }
});