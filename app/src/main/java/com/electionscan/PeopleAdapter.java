package com.electionscan;

import android.os.AsyncTask;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class PeopleAdapter extends RecyclerView.Adapter<PeopleAdapter.PersonHolder> {

    @LayoutRes
    private int personLayout;
    private ArrayList<Person> people;
    private boolean instantVoteEnabled; //Whether a vote action is available via people' checkbox
    private boolean voteOptionVisible;
    private boolean contactOptionVisible;
    private SimpleDateFormat timeFormat;

    private PersonInterface personInterface;
    private SwipeRefreshLayout refreshLayout;

    @ColorInt
    private int markeredColor;
    @ColorInt
    private int defaultColor;

    public interface PersonInterface {
        void onPersonClicked(int personId);

        void onPersonVote(@NonNull String personKey, boolean voted);

        void onPersonContacted(@NonNull String personKey, boolean contacted);

        void onPersonColored(@NonNull String personKey, boolean toColor);
    }

    public PeopleAdapter(int personLayout, @NonNull PersonInterface personInterface,
                         boolean instantVoteEnabled, boolean voteOptionVisible,
                         boolean contactOptionVisible, @NonNull SwipeRefreshLayout refreshLayout,
                         @ColorInt int markeredColor, @ColorInt int defaultColor) {
        this.personLayout = personLayout;
        this.people = new ArrayList<>();
        this.personInterface = personInterface;
        this.instantVoteEnabled = instantVoteEnabled;
        this.voteOptionVisible = voteOptionVisible;
        this.contactOptionVisible = contactOptionVisible;
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
        this.refreshLayout = refreshLayout;
        this.markeredColor = markeredColor;
        this.defaultColor = defaultColor;
    }

    class PersonHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {
        private TextView tvName, tvId, tvId_Inside_Kalpi, tvTimeOfContact;
        private CheckBox checkboxvoted;
        private AppCompatImageView imgCircle, imgContacted;
        private View container, layoutVoteClick;

        @NonNull
        private String personKey;
        private boolean contacted;
        private boolean colored;

        PersonHolder(View itemView) {
            super(itemView);

            container = itemView.findViewById(R.id.container);
            layoutVoteClick = itemView.findViewById(R.id.layoutClick);
            tvName = itemView.findViewById(R.id.tvName);
            tvId = itemView.findViewById(R.id.tvId);
            tvId_Inside_Kalpi = itemView.findViewById(R.id.tvId_Inside_Kalpi);
            tvTimeOfContact = itemView.findViewById(R.id.tvTimeOfContact);
            checkboxvoted = itemView.findViewById(R.id.checkboxVoted);
            checkboxvoted.setEnabled(instantVoteEnabled);
            imgCircle = itemView.findViewById(R.id.imgCircle);
            imgContacted = itemView.findViewById(R.id.imgContacted);
            personKey = "";
            contacted = false;
            colored = false;

            imgContacted.setVisibility(contactOptionVisible ? View.VISIBLE : View.GONE);
            layoutVoteClick.setVisibility(instantVoteEnabled ? View.VISIBLE : View.GONE);
            checkboxvoted.setVisibility(voteOptionVisible ? View.VISIBLE : View.GONE);
            tvTimeOfContact.setVisibility(View.GONE);

            imgContacted.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    personInterface.onPersonContacted(personKey, !contacted);
                }
            });

            container.setOnClickListener(this);
            container.setOnLongClickListener(this);
        }

        public void setPersonKey(@NonNull String key) {
            this.personKey = key;
        }

        public void setContacted(boolean contacted, long timeOfContact) {
            this.contacted = contacted;

            //Create the time label
            if (timeOfContact != 0)
                tvTimeOfContact.setText(timeFormat.format(new Date(timeOfContact)));

            if (contacted && timeOfContact != 0 && contactOptionVisible)
                tvTimeOfContact.setVisibility(View.VISIBLE);
            else
                tvTimeOfContact.setVisibility(View.GONE);
        }

        public void setColored(boolean colored) {
            this.colored = colored;
            container.setBackgroundColor(colored && contactOptionVisible ?
                    markeredColor : defaultColor);
        }

        public void setId(int id) {
            String idString = String.valueOf(id);
            //Add zeros at the beginning
            int zeroAmount = 9 - idString.length(); //9 = max id length
            StringBuilder buildId = new StringBuilder();
            for (int k = 0; k < zeroAmount; k++) buildId.append('0');
            idString = buildId.toString() + idString;
            tvId.setText(idString);
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.checkboxVoted:
                    personInterface.onPersonVote(personKey, checkboxvoted.isChecked());
                    break;
                case R.id.layoutClick:
                    personInterface.onPersonVote(personKey, !checkboxvoted.isChecked());
                    break;
                default:
                    int id = Integer.parseInt(this.tvId.getText().toString());
                    personInterface.onPersonClicked(id);
                    break;
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (contactOptionVisible) {
                personInterface.onPersonColored(personKey, !colored);
                return true;
            }
            return false;
        }
    }

    @NonNull
    @Override
    public PersonHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(personLayout, viewGroup, false);
        return new PersonHolder(view);
    }

    @Override
    public int getItemCount() {
        return this.people.size();
    }

    @Override
    public void onBindViewHolder(@NonNull PersonHolder personHolder, int i) {
        Person person = people.get(i);
        String name = person.getLastName() + " " + person.getFirstName();
        personHolder.tvName.setText(name);

        personHolder.setId(person.getId());
        personHolder.setPersonKey(person.getKey());
        personHolder.setContacted(person.isContacted(), person.getTimeOfContact());
        personHolder.setColored(person.isColored());

        personHolder.tvId_Inside_Kalpi.setText(String.valueOf(person.getId_inside_kalpi()));
        personHolder.imgCircle.setImageResource(person.hasVoted() ?
                R.drawable.ic_account_circle_primarycolor_24dp :
                R.drawable.ic_account_circle_graycolor_24dp);

        personHolder.imgContacted.setImageResource(person.isContacted() ?
                R.drawable.ic_phone_on_24dp :
                R.drawable.ic_call_end_24dp);

        if (personHolder.checkboxvoted.isChecked() != person.hasVoted())
            personHolder.checkboxvoted.setChecked(person.hasVoted());

        personHolder.checkboxvoted.setOnClickListener(personHolder);
        personHolder.layoutVoteClick.setOnClickListener(personHolder);
    }

    public void setPeople(@NonNull ArrayList<Person> newList) {
        new Differentiate().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, newList);

        /*DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return people.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return people.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Person newPerson = newList.get(newItemPosition);
                Person oldPerson = people.get(oldItemPosition);
                return newPerson.equals(oldPerson);
            }
        });
        people = newList;
        result.dispatchUpdatesTo(this);*/
    }

    class Differentiate extends AsyncTask<ArrayList<Person>, Void, DiffUtil.DiffResult> {
        ArrayList<Person> newList;

        @Override
        protected DiffUtil.DiffResult doInBackground(ArrayList<Person>... arrayLists) {
            newList = arrayLists[0];

            return DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return people.size();
                }

                @Override
                public int getNewListSize() {
                    return newList.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return people.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    Person newPerson = newList.get(newItemPosition);
                    Person oldPerson = people.get(oldItemPosition);
                    return newPerson.equals(oldPerson);
                }
            });
        }

        @Override
        protected void onPostExecute(DiffUtil.DiffResult diffResult) {
            people = newList;
            diffResult.dispatchUpdatesTo(PeopleAdapter.this);
            newList = null;
            refreshLayout.setRefreshing(false);
            super.onPostExecute(diffResult);
        }
    }
}
